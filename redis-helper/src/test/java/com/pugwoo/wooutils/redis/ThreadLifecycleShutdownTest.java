package com.pugwoo.wooutils.redis;

import com.pugwoo.wooutils.cache.HiSpeedCache;
import com.pugwoo.wooutils.cache.HiSpeedCacheAspect;
import com.pugwoo.wooutils.redis.impl.RedisHelperImpl;
import com.pugwoo.wooutils.redis.impl.RedisMsgQueue;
import com.pugwoo.wooutils.redis.impl.RedisPubSub;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台线程关停测试：不依赖真实 Redis，用假 RedisHelper 模拟"阻塞在 socket 读、interrupt 打不断"的行为
 * （按传入的超时秒数睡满、期间吞掉中断），验证各组件 shutdown()/destroy() 后线程能真正退出、
 * Spring 上下文关闭时 JVM 不会被非守护线程挂住。
 */
public class ThreadLifecycleShutdownTest {

    public static class ReceiveService {
        @ReceiveMsg(topic = "test-topic")
        public void onMsg(RedisMsg msg) {
        }
    }

    public static class SubscribeService {
        @Subscribe(channel = "test-channel")
        public void onMsg(String msg) {
        }
    }

    @Test
    public void receiveProcessorShutdownStopsBlockedConsumer() throws Exception {
        ReceiveMsgProcessor processor = new ReceiveMsgProcessor();
        setField(processor, "redisHelper", fakeRedisHelper());
        processor.setApplicationContext(fakeApplicationContext(new ReceiveService()));
        processor.afterPropertiesSet();

        waitForThreadCount("ReceiveMsg-Consumer-", 1, 5000);
        long start = System.currentTimeMillis();
        processor.shutdown();
        long cost = System.currentTimeMillis() - start;

        assertTrue(processor.isShuttingDown());
        assertEquals(0, countAliveThreads("ReceiveMsg-Consumer-"),
                "shutdown 后消费者线程应已退出, cost:" + cost + "ms");
        assertTrue(cost < 14000, "shutdown 应在一个轮询周期左右完成, 实际:" + cost + "ms");

        // 幂等：第二次 shutdown 应立即返回（Spring 会同时走 DisposableBean 与推断的 shutdown 两条销毁路径）
        long start2 = System.currentTimeMillis();
        processor.destroy();
        processor.close();
        assertTrue(System.currentTimeMillis() - start2 < 1000, "重复 shutdown 应立即返回");
    }

    @Test
    public void subscribeProcessorShutdownStopsBlockedSubscriber() throws Exception {
        SubscribeProcessor processor = new SubscribeProcessor();
        setField(processor, "redisHelper", fakeRedisHelper());
        processor.setApplicationContext(fakeApplicationContext(new SubscribeService()));
        processor.afterPropertiesSet();

        waitForThreadCount("Subscribe-Consumer-", 1, 5000);
        long start = System.currentTimeMillis();
        processor.shutdown();
        long cost = System.currentTimeMillis() - start;

        assertTrue(processor.isShuttingDown());
        assertEquals(0, countAliveThreads("Subscribe-Consumer-"),
                "shutdown 后订阅者线程应已退出, cost:" + cost + "ms");
        assertTrue(cost < 14000, "shutdown 应在一个轮询周期左右完成, 实际:" + cost + "ms");

        long start2 = System.currentTimeMillis();
        processor.destroy();
        processor.close();
        assertTrue(System.currentTimeMillis() - start2 < 1000, "重复 shutdown 应立即返回");
    }

    @Configuration
    static class ShutdownTestConfig {
        @Bean
        public RedisHelper redisHelper() {
            return fakeRedisHelper();
        }

        @Bean
        public ReceiveService receiveService() {
            return new ReceiveService();
        }

        @Bean
        public ReceiveMsgProcessor receiveMsgProcessor() {
            return new ReceiveMsgProcessor();
        }
    }

    /**真实 Spring 上下文关闭时应触发处理器 destroy 并停掉消费者线程（且 DisposableBean 与推断销毁方法重复回调不报错）*/
    @Test
    public void springContextCloseStopsReceiveConsumer() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ShutdownTestConfig.class);
        waitForThreadCount("ReceiveMsg-Consumer-", 1, 5000);

        context.close();

        assertEquals(0, countAliveThreads("ReceiveMsg-Consumer-"),
                "Spring 上下文关闭后消费者线程应已退出");
    }

    @Test
    public void recoverMsgTaskShutdownStopsCleanAndRenewalThreads() throws Exception {
        RedisMsgQueue.RecoverMsgTask task = new RedisMsgQueue.RecoverMsgTask(fakeRedisHelper());
        task.setName("RedisMsgQueue.RecoverMsgTask");
        task.start();

        waitForThreadCount("RedisMsgQueue.RecoverMsg", 2, 5000);
        long start = System.currentTimeMillis();
        task.shutdown();
        long cost = System.currentTimeMillis() - start;

        assertFalse(task.isRunning());
        assertFalse(task.isAlive(), "RecoverMsgTask 主线程应已退出");
        assertEquals(0, countAliveThreads("RedisMsgQueue.RecoverMsg"),
                "清理主线程与续期子线程都应退出, cost:" + cost + "ms");
        assertTrue(cost < 9000, "shutdown 应快速完成, 实际:" + cost + "ms");
    }

    @Test
    public void redisHelperImplCloseIsIdempotentWithoutRedis() {
        RedisHelperImpl helper = new RedisHelperImpl();
        helper.close();
        helper.close();
        helper.destroy();
    }

    @Test
    public void redisSyncAspectDoesNotDuplicateHeartbeatThreadAndShutdownStopsIt() throws Exception {
        RedisSyncAspect aspect = new RedisSyncAspect();
        aspect.setRedisHelper(fakeRedisHelper());
        aspect.afterPropertiesSet();
        aspect.afterPropertiesSet(); // 重复初始化不得再新建一条心跳线程

        waitForThreadCount("RedisSyncAspect-heartbeat-renewal-thread", 1, 5000);
        assertEquals(1, countAliveThreads("RedisSyncAspect-heartbeat-renewal-thread"),
                "重复 afterPropertiesSet 不应产生第二条心跳线程");

        aspect.shutdown();
        assertEquals(0, countAliveThreads("RedisSyncAspect-heartbeat-renewal-thread"),
                "shutdown 后心跳线程应已退出");
        assertEquals(0, countAliveThreads("Redis-Sync-Heartbeat-Renewal-Thread-Pool"),
                "shutdown 后续约线程池应已终止");

        aspect.destroy();
        aspect.close();
    }

    /**
     * 用极简 RESP 假服务端 + 真实 Jedis 验证 RedisPubSub 的超时订阅：
     * 服务端只回订阅确认、之后不发消息，subscribe(channel, 1) 应在约 1 秒后返回 null 而不是永久阻塞。
     */
    @Test
    public void redisPubSubSubscribeWithTimeoutReturnsNullWhenNoMessage() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(false)) {
            long start = System.currentTimeMillis();
            String message = RedisPubSub.subscribe(jedisBackedHelper(server.getPort()), "test-channel", 1);
            long cost = System.currentTimeMillis() - start;
            assertEquals(null, message);
            assertTrue(cost >= 900 && cost < 8000, "超时订阅应在约1秒后返回, 实际:" + cost + "ms");
        }
    }

    /**假服务端在订阅确认后立即发一条消息，subscribe(channel, 5) 应立即收到该消息而不是等超时*/
    @Test
    public void redisPubSubSubscribeWithTimeoutStillReceivesMessage() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(true)) {
            long start = System.currentTimeMillis();
            String message = RedisPubSub.subscribe(jedisBackedHelper(server.getPort()), "test-channel", 5);
            long cost = System.currentTimeMillis() - start;
            assertEquals("hello", message);
            assertTrue(cost < 4000, "收到消息应立即返回, 实际:" + cost + "ms");
        }
    }

    @Test
    public void hiSpeedCacheAspectShutdownStopsBackgroundThreads() throws Exception {
        HiSpeedCacheAspect aspect = new HiSpeedCacheAspect();
        Method startThread = HiSpeedCacheAspect.class.getDeclaredMethod("startThread", HiSpeedCache.class);
        startThread.setAccessible(true);
        startThread.invoke(aspect, fakeHiSpeedCacheAnnotation());

        waitForThreadCount("HiSpeedCache-clean-thread", 1, 5000);
        waitForThreadCount("HiSpeedCache-update-thread", 1, 5000);

        aspect.shutdown();

        assertTrue(aspect.isShuttingDown());
        assertEquals(0, countAliveThreads("HiSpeedCache-"), "shutdown 后后台线程应已退出");
        Field field = HiSpeedCacheAspect.class.getDeclaredField("executorService");
        field.setAccessible(true);
        assertTrue(((ExecutorService) field.get(aspect)).isTerminated(), "shutdown 后更新线程池应已终止");

        aspect.destroy();
        aspect.close();
    }

    // ---------- fakes ----------

    /**
     * 假 RedisHelper：receive/subscribe 按超时秒数睡满且吞掉中断，模拟真实 Jedis 阻塞读的行为；
     * requireLock 永远拿不到锁，让 RecoverMsgTask 停在 30 秒等待上以验证 interrupt 退出。
     */
    private static RedisHelper fakeRedisHelper() {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("receive".equals(name) && args != null && args.length == 3) {
                sleepUninterruptibly(((Integer) args[1]) * 1000L);
                return null;
            }
            if ("subscribe".equals(name)) {
                if (args != null && args.length == 2) {
                    sleepUninterruptibly(((Integer) args[1]) * 1000L);
                } else {
                    sleepUninterruptibly(60000L);
                }
                return null;
            }
            if ("requireLock".equals(name)) {
                return null;
            }
            if ("toString".equals(name)) {
                return "fakeRedisHelper";
            }
            return defaultValue(method.getReturnType());
        };
        return (RedisHelper) Proxy.newProxyInstance(ThreadLifecycleShutdownTest.class.getClassLoader(),
                new Class<?>[]{RedisHelper.class}, handler);
    }

    /**只桩住处理器扫描用到的两个方法：getBeanDefinitionNames / getBean(String)*/
    private static ApplicationContext fakeApplicationContext(Object bean) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getBeanDefinitionNames".equals(method.getName())) {
                return new String[]{"testService"};
            }
            if ("getBean".equals(method.getName()) && args != null && args.length == 1) {
                return bean;
            }
            if ("toString".equals(method.getName())) {
                return "fakeApplicationContext";
            }
            return defaultValue(method.getReturnType());
        };
        return (ApplicationContext) Proxy.newProxyInstance(ThreadLifecycleShutdownTest.class.getClassLoader(),
                new Class<?>[]{ApplicationContext.class}, handler);
    }

    private static HiSpeedCache fakeHiSpeedCacheAnnotation() {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("continueFetchSecond".equals(method.getName())) {
                return 60;
            }
            if ("annotationType".equals(method.getName())) {
                return HiSpeedCache.class;
            }
            if ("toString".equals(method.getName())) {
                return "fakeHiSpeedCache";
            }
            return defaultValue(method.getReturnType());
        };
        return (HiSpeedCache) Proxy.newProxyInstance(ThreadLifecycleShutdownTest.class.getClassLoader(),
                new Class<?>[]{HiSpeedCache.class}, handler);
    }

    /**用真实 Jedis 连接假服务端的 RedisHelper，只桩住 execute，其余方法返回默认值*/
    private static RedisHelper jedisBackedHelper(int port) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("execute".equals(method.getName()) && args != null && args.length == 1) {
                @SuppressWarnings("unchecked")
                Function<Jedis, Object> func = (Function<Jedis, Object>) args[0];
                Jedis jedis = new Jedis("127.0.0.1", port, 2000);
                try {
                    return func.apply(jedis);
                } finally {
                    jedis.close();
                }
            }
            if ("toString".equals(method.getName())) {
                return "jedisBackedHelper";
            }
            return defaultValue(method.getReturnType());
        };
        return (RedisHelper) Proxy.newProxyInstance(ThreadLifecycleShutdownTest.class.getClassLoader(),
                new Class<?>[]{RedisHelper.class}, handler);
    }

    /**
     * 极简 RESP 假服务端：能解析 RESP 数组命令，对 SUBSCRIBE 回订阅确认（sendMessage 时再补发一条消息），
     * 对 UNSUBSCRIBE 回取消确认，对其它命令回 +OK。足够让真实 Jedis 走完订阅流程。
     */
    private static final class FakeRedisServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private volatile boolean closed = false;

        FakeRedisServer(boolean sendMessage) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.thread = new Thread(() -> serve(sendMessage), "fake-redis-server");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        private void serve(boolean sendMessage) {
            try (Socket socket = serverSocket.accept()) {
                InputStream in = new BufferedInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                List<String> command;
                while (!closed && (command = readCommand(in)) != null) {
                    if (command.isEmpty()) {
                        continue;
                    }
                    String cmd = command.get(0).toUpperCase();
                    if ("SUBSCRIBE".equals(cmd) && command.size() >= 2) {
                        String channel = command.get(1);
                        writeFrame(out, "subscribe", channel, 1);
                        if (sendMessage) {
                            writeMessageFrame(out, channel, "hello");
                        }
                    } else if ("UNSUBSCRIBE".equals(cmd)) {
                        String channel = command.size() >= 2 ? command.get(1) : "";
                        writeFrame(out, "unsubscribe", channel, 0);
                    } else {
                        out.write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // 客户端超时后会把连接标记 broken 并关闭，服务端读到异常属正常收尾
            }
        }

        private static List<String> readCommand(InputStream in) throws IOException {
            int first = in.read();
            if (first == -1) {
                return null;
            }
            if (first != '*') {
                throw new IOException("unexpected RESP start: " + (char) first);
            }
            int count = Integer.parseInt(readLine(in));
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int dollar = in.read();
                if (dollar != '$') {
                    throw new IOException("unexpected bulk start: " + (char) dollar);
                }
                int len = Integer.parseInt(readLine(in));
                byte[] buf = new byte[len];
                int read = 0;
                while (read < len) {
                    int n = in.read(buf, read, len - read);
                    if (n == -1) {
                        return null;
                    }
                    read += n;
                }
                in.read(); // \r
                in.read(); // \n
                parts.add(new String(buf, StandardCharsets.UTF_8));
            }
            return parts;
        }

        private static String readLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            boolean cr = false;
            while ((c = in.read()) != -1) {
                if (cr && c == '\n') {
                    break;
                }
                cr = (c == '\r');
                if (!cr) {
                    sb.append((char) c);
                }
            }
            return sb.toString();
        }

        private static void writeFrame(OutputStream out, String kind, String channel, int count) throws IOException {
            String frame = "*3\r\n$" + kind.length() + "\r\n" + kind + "\r\n"
                    + "$" + channel.length() + "\r\n" + channel + "\r\n"
                    + ":" + count + "\r\n";
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        private static void writeMessageFrame(OutputStream out, String channel, String message) throws IOException {
            String frame = "*3\r\n$7\r\nmessage\r\n"
                    + "$" + channel.length() + "\r\n" + channel + "\r\n"
                    + "$" + message.length() + "\r\n" + message + "\r\n";
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
        }
    }

    // ---------- helpers ----------

    /**睡满指定毫秒，期间吞掉中断，模拟对 interrupt 不敏感的 socket 阻塞读*/
    private static void sleepUninterruptibly(long millis) {
        long deadline = System.currentTimeMillis() + millis;
        long remain;
        while ((remain = deadline - System.currentTimeMillis()) > 0) {
            try {
                Thread.sleep(remain);
            } catch (InterruptedException ignored) {
                // 故意吞掉：真实 Jedis 阻塞读同样不会被 interrupt 唤醒
            }
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == double.class || type == float.class) {
            return 0.0;
        }
        return null;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int countAliveThreads(String namePrefix) {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName() != null && thread.getName().startsWith(namePrefix)) {
                count++;
            }
        }
        return count;
    }

    private static void waitForThreadCount(String namePrefix, int expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (countAliveThreads(namePrefix) >= expected) {
                return;
            }
            Thread.sleep(50);
        }
        assertEquals(expected, countAliveThreads(namePrefix),
                "等待线程 " + namePrefix + " 达到 " + expected + " 个超时");
    }
}
