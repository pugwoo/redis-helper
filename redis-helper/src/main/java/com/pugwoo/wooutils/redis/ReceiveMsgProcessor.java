package com.pugwoo.wooutils.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReceiveMsg 注解的处理器
 * 负责扫描带有 @ReceiveMsg 注解的方法，并启动独立的线程来接收和处理消息
 * 
 * @author pugwoo
 */
public class ReceiveMsgProcessor implements InitializingBean, ApplicationContextAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveMsgProcessor.class);

    @Autowired
    private RedisHelper redisHelper;

    private ApplicationContext applicationContext;

    private final long startTimestamp = System.currentTimeMillis();

    /**
     * 消息接收线程池
     *
     * 设计说明：
     * 1. 每个消费者线程会永久阻塞在 receive() 上等待消息，因此必须为每个消费者分配独立线程
     * 2. 使用 SynchronousQueue（容量为0）确保每个任务立即获得线程，不会在队列中等待
     * 3. 核心线程数设为0，所有线程都是按需创建的
     * 4. 最大线程数设为 Integer.MAX_VALUE，理论上不限制消费者数量
     * 5. 空闲线程60秒后回收，避免资源浪费
     * 6. 拒绝策略使用 AbortPolicy，如果无法创建线程则抛出异常（实际上不会触发）
     */
    private ThreadPoolExecutor receiveMsgThreadPool;

    /**
     * 记录所有启动的消费者线程，用于优雅关闭
     */
    private final List<Future<?>> consumerFutures = new CopyOnWriteArrayList<>();

    /**
     * 是否正在关闭
     */
    private volatile boolean isShuttingDown = false;

    public ReceiveMsgProcessor() {
        // 创建线程池
        receiveMsgThreadPool = new ThreadPoolExecutor(
                0,                      // 核心线程数为0，按需创建
                200,      // 最大线程数，默认200
                60L,                    // 空闲线程存活时间
                TimeUnit.SECONDS,
                new SynchronousQueue<>(), // 直接提交，不缓存任务
                new ReceiveMsgThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy() // 拒绝策略：抛出异常
        );
    }

    public ReceiveMsgProcessor(int maxConsumers) {
        // 创建线程池
        receiveMsgThreadPool = new ThreadPoolExecutor(
                0,                      // 核心线程数为0，按需创建
                maxConsumers,      // 最大线程数
                60L,                    // 空闲线程存活时间
                TimeUnit.SECONDS,
                new SynchronousQueue<>(), // 直接提交，不缓存任务
                new ReceiveMsgThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy() // 拒绝策略：抛出异常
        );
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (redisHelper == null) {
            LOGGER.error("redisHelper is null, ReceiveMsgProcessor will not start any consumer");
            return;
        }

        // 扫描所有带有 @ReceiveMsg 注解的方法
        scanAndStartConsumers();

        long cost = System.currentTimeMillis() - startTimestamp;
        LOGGER.info("@ReceiveMsg init success, started {} consumer threads, cost:{} ms.", 
                consumerFutures.size(), cost);
    }

    /**
     * 扫描所有带有 @ReceiveMsg 注解的方法并启动消费者线程
     */
    private void scanAndStartConsumers() {
        // 获取所有 Bean 的名称（包括通过 @Bean 方法注册的）
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> clazz = bean.getClass();

                // 处理 CGLIB 代理类
                if (clazz.getName().contains("$$")) {
                    clazz = clazz.getSuperclass();
                }

                for (Method method : clazz.getDeclaredMethods()) {
                    // 处理单个 @ReceiveMsg 注解
                    if (method.isAnnotationPresent(ReceiveMsg.class)) {
                        ReceiveMsg receiveMsg = method.getAnnotation(ReceiveMsg.class);
                        startConsumer(bean, method, receiveMsg);
                    }

                    // 处理多个 @ReceiveMsg 注解
                    if (method.isAnnotationPresent(ReceiveMsgs.class)) {
                        ReceiveMsgs receiveMsgs = method.getAnnotation(ReceiveMsgs.class);
                        for (ReceiveMsg receiveMsg : receiveMsgs.value()) {
                            startConsumer(bean, method, receiveMsg);
                        }
                    }
                }
            } catch (Exception e) {
                // 某些特殊的 Bean（如 BeanPostProcessor）可能无法直接获取，跳过即可
                LOGGER.debug("Skip bean: {}, reason: {}", beanName, e.getMessage());
            }
        }
    }

    /**
     * 启动消费者线程
     */
    private void startConsumer(Object bean, Method method, ReceiveMsg receiveMsg) {
        String topic = receiveMsg.topic();
        int ackTimeoutSec = receiveMsg.ackTimeoutSec();
        int consumerThreads = receiveMsg.consumerThreads();

        // 验证方法签名
        if (!validateMethodSignature(method)) {
            LOGGER.error("Method {} has invalid signature for @ReceiveMsg, must have exactly one RedisMsg parameter",
                    method.getName());
            return;
        }

        // 启动指定数量的消费者线程
        for (int i = 0; i < consumerThreads; i++) {
            Future<?> future = receiveMsgThreadPool.submit(() -> {
                String threadName = Thread.currentThread().getName();
                LOGGER.info("Consumer thread started: {}, topic: {}, method: {}.{}", 
                        threadName, topic, bean.getClass().getSimpleName(), method.getName());

                while (!isShuttingDown) {
                    try {
                        // 阻塞等待消息，永久等待
                        RedisMsg msg = redisHelper.receive(topic, -1,
                                ackTimeoutSec > 0 ? ackTimeoutSec : null);

                        if (msg == null) {
                            // 没有接收到消息，继续等待
                            continue;
                        }

                        LOGGER.debug("Received message: topic={}, uuid={}, thread={}",
                                topic, msg.getUuid(), threadName);

                        try {
                            // 调用处理方法
                            method.setAccessible(true);
                            method.invoke(bean, msg);

                            // 处理成功，发送 ack
                            boolean ackResult = redisHelper.ack(topic, msg.getUuid());
                            if (ackResult) {
                                LOGGER.debug("Message acked: topic={}, uuid={}", topic, msg.getUuid());
                            } else {
                                LOGGER.warn("Message ack failed: topic={}, uuid={}", topic, msg.getUuid());
                            }

                        } catch (Exception e) {
                            // 处理失败，发送 nack
                            LOGGER.error("Error processing message: topic={}, uuid={}, error={}",
                                    topic, msg.getUuid(), e.getMessage(), e);

                            boolean nackResult = redisHelper.nack(topic, msg.getUuid());
                            if (nackResult) {
                                LOGGER.info("Message nacked: topic={}, uuid={}", topic, msg.getUuid());
                            } else {
                                LOGGER.warn("Message nack failed: topic={}, uuid={}", topic, msg.getUuid());
                            }
                        }

                    } catch (Exception e) {
                        if (!isShuttingDown) {
                            LOGGER.error("Error in consumer thread: topic={}, thread={}, error={}",
                                    topic, threadName, e.getMessage(), e);
                            // 发生异常后，等待一段时间再继续，避免快速失败循环
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }

                LOGGER.info("Consumer thread stopped: {}, topic: {}", threadName, topic);
            });

            consumerFutures.add(future);
        }

        LOGGER.info("Started {} consumer thread(s) for topic: {}, method: {}.{}",
                consumerThreads, topic, bean.getClass().getSimpleName(), method.getName());
    }

    /**
     * 验证方法签名是否正确
     * 方法必须有且仅有一个 RedisMsg 类型的参数
     */
    private boolean validateMethodSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes.length == 1 && parameterTypes[0] == RedisMsg.class;
    }

    /**
     * 优雅关闭
     */
    public void shutdown() {
        LOGGER.info("Shutting down ReceiveMsgProcessor...");
        isShuttingDown = true;

        // 取消所有消费者任务
        for (Future<?> future : consumerFutures) {
            future.cancel(true);
        }

        // 关闭线程池
        receiveMsgThreadPool.shutdown();
        try {
            if (!receiveMsgThreadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                receiveMsgThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            receiveMsgThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("ReceiveMsgProcessor shutdown complete");
    }

    /**
     * 自定义线程工厂
     */
    private static class ReceiveMsgThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "ReceiveMsg-Consumer-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(false); // 非守护线程，确保消息处理完成
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

}

