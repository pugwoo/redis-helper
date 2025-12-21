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
 * Subscribe 注解的处理器
 * 负责扫描带有 @Subscribe 注解的方法，并启动独立的线程来订阅和处理消息
 * 
 * @author pugwoo
 */
public class SubscribeProcessor implements InitializingBean, ApplicationContextAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeProcessor.class);

    @Autowired
    private RedisHelper redisHelper;

    private ApplicationContext applicationContext;

    private final long startTimestamp = System.currentTimeMillis();

    /**
     * 消息订阅线程池
     *
     * 设计说明：
     * 1. 每个订阅者线程会永久阻塞在 subscribe() 上等待消息，因此必须为每个订阅者分配独立线程
     * 2. 使用 SynchronousQueue（容量为0）确保每个任务立即获得线程，不会在队列中等待
     * 3. 核心线程数设为0，所有线程都是按需创建的
     * 4. 最大线程数设为 200，理论上支持大量订阅者
     * 5. 空闲线程60秒后回收，避免资源浪费
     * 6. 拒绝策略使用 AbortPolicy，如果无法创建线程则抛出异常
     */
    private ThreadPoolExecutor subscribeThreadPool;

    /**
     * 记录所有启动的订阅者线程，用于优雅关闭
     */
    private final List<Future<?>> subscriberFutures = new CopyOnWriteArrayList<>();

    /**
     * 是否正在关闭
     */
    private volatile boolean isShuttingDown = false;

    public SubscribeProcessor() {
        // 创建线程池
        subscribeThreadPool = new ThreadPoolExecutor(
                0,                      // 核心线程数为0，按需创建
                200,                    // 最大线程数，默认200
                60L,                    // 空闲线程存活时间
                TimeUnit.SECONDS,
                new SynchronousQueue<>(), // 直接提交，不缓存任务
                new SubscribeThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy() // 拒绝策略：抛出异常
        );
    }

    public SubscribeProcessor(int maxSubscribers) {
        // 创建线程池
        subscribeThreadPool = new ThreadPoolExecutor(
                0,                      // 核心线程数为0，按需创建
                maxSubscribers,         // 最大线程数
                60L,                    // 空闲线程存活时间
                TimeUnit.SECONDS,
                new SynchronousQueue<>(), // 直接提交，不缓存任务
                new SubscribeThreadFactory(),
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
            LOGGER.error("redisHelper is null, SubscribeProcessor will not start any subscriber");
            return;
        }

        // 扫描所有带有 @Subscribe 注解的方法
        scanAndStartSubscribers();

        long cost = System.currentTimeMillis() - startTimestamp;
        LOGGER.info("@Subscribe init success, started {} subscriber threads, cost:{} ms.", 
                subscriberFutures.size(), cost);
    }

    /**
     * 扫描所有带有 @Subscribe 注解的方法并启动订阅者线程
     */
    private void scanAndStartSubscribers() {
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
                    // 处理单个 @Subscribe 注解
                    if (method.isAnnotationPresent(Subscribe.class)) {
                        Subscribe subscribe = method.getAnnotation(Subscribe.class);
                        startSubscriber(bean, method, subscribe);
                    }

                    // 处理多个 @Subscribe 注解
                    if (method.isAnnotationPresent(Subscribes.class)) {
                        Subscribes subscribes = method.getAnnotation(Subscribes.class);
                        for (Subscribe subscribe : subscribes.value()) {
                            startSubscriber(bean, method, subscribe);
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
     * 启动订阅者线程
     */
    private void startSubscriber(Object bean, Method method, Subscribe subscribe) {
        String channel = subscribe.channel();
        int consumerThreads = subscribe.consumerThreads();

        // 验证方法签名
        if (!validateMethodSignature(method)) {
            LOGGER.error("Method {} has invalid signature for @Subscribe, must have exactly one String parameter",
                    method.getName());
            return;
        }

        // 启动指定数量的订阅者线程
        for (int i = 0; i < consumerThreads; i++) {
            Future<?> future = subscribeThreadPool.submit(() -> {
                String threadName = Thread.currentThread().getName();
                LOGGER.info("Subscriber thread started: {}, channel: {}, method: {}.{}",
                        threadName, channel, bean.getClass().getSimpleName(), method.getName());

                while (!isShuttingDown) {
                    try {
                        // 阻塞等待消息
                        String message = redisHelper.subscribe(channel);

                        if (message == null) {
                            // 没有接收到消息，继续等待
                            continue;
                        }

                        LOGGER.debug("Received message: channel={}, message={}, thread={}",
                                channel, message, threadName);

                        try {
                            // 调用处理方法
                            method.setAccessible(true);
                            method.invoke(bean, message);

                            LOGGER.debug("Message processed: channel={}, message={}", channel, message);

                        } catch (Exception e) {
                            // 处理失败，记录错误
                            LOGGER.error("Error processing message: channel={}, message={}, error={}",
                                    channel, message, e.getMessage(), e);
                        }

                    } catch (Exception e) {
                        if (!isShuttingDown) {
                            LOGGER.error("Error in subscriber thread: channel={}, thread={}, error={}",
                                    channel, threadName, e.getMessage(), e);
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

                LOGGER.info("Subscriber thread stopped: {}, channel: {}", threadName, channel);
            });

            subscriberFutures.add(future);
        }

        LOGGER.info("Started {} subscriber thread(s) for channel: {}, method: {}.{}",
                consumerThreads, channel, bean.getClass().getSimpleName(), method.getName());
    }

    /**
     * 验证方法签名是否正确
     * 方法必须有且仅有一个 String 类型的参数
     */
    private boolean validateMethodSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes.length == 1 && parameterTypes[0] == String.class;
    }

    /**
     * 优雅关闭
     */
    public void shutdown() {
        LOGGER.info("Shutting down SubscribeProcessor...");
        isShuttingDown = true;

        // 取消所有订阅者任务
        for (Future<?> future : subscriberFutures) {
            future.cancel(true);
        }

        // 关闭线程池
        subscribeThreadPool.shutdown();
        try {
            if (!subscribeThreadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                subscribeThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            subscribeThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("SubscribeProcessor shutdown complete");
    }

    /**
     * 自定义线程工厂
     */
    private static class SubscribeThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "Subscribe-Consumer-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(false); // 非守护线程，确保消息处理完成
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

}

