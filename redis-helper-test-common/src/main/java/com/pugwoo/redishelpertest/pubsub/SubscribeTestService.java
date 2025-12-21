package com.pugwoo.redishelpertest.pubsub;

import com.pugwoo.wooutils.redis.Subscribe;
import com.pugwoo.wooutils.redis.Subscribes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试 @Subscribe 注解的服务类
 */
public class SubscribeTestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeTestService.class);

    // 用于测试验证的数据结构
    private final CopyOnWriteArrayList<String> receivedMessages = new CopyOnWriteArrayList<>();
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, String> messageMap = new ConcurrentHashMap<>();

    /**
     * 测试基本的消息订阅功能
     */
    @Subscribe(channel = "test-subscribe-basic")
    public void handleBasicMessage(String message) {
        LOGGER.info("Received basic message: {}", message);
        receivedMessages.add(message);
        processedCount.incrementAndGet();
    }

    /**
     * 测试多线程订阅
     */
    @Subscribe(channel = "test-subscribe-multi-thread", consumerThreads = 3)
    public void handleMessageWithMultiThread(String message) {
        LOGGER.info("Received message in multi-thread mode: {} by thread {}", 
                message, Thread.currentThread().getName());
        receivedMessages.add(message);
        processedCount.incrementAndGet();
    }

    /**
     * 测试处理异常的情况
     */
    @Subscribe(channel = "test-subscribe-exception")
    public void handleMessageWithException(String message) {
        LOGGER.info("Received message that will throw exception: {}", message);
        failedCount.incrementAndGet();
        throw new RuntimeException("Test exception for message: " + message);
    }

    /**
     * 测试处理慢消息
     */
    @Subscribe(channel = "test-subscribe-slow")
    public void handleSlowMessage(String message) throws InterruptedException {
        LOGGER.info("Received slow message: {}", message);
        Thread.sleep(2000); // 模拟慢处理
        receivedMessages.add(message);
        processedCount.incrementAndGet();
    }

    /**
     * 测试多个 channel
     */
    @Subscribes({
        @Subscribe(channel = "test-subscribe-multi-channel-1"),
        @Subscribe(channel = "test-subscribe-multi-channel-2")
    })
    public void handleMultiChannel(String message) {
        LOGGER.info("Received message from multi-channel: {}", message);
        messageMap.put(Thread.currentThread().getName(), message);
        receivedMessages.add(message);
        processedCount.incrementAndGet();
    }

    /**
     * 测试返回值（返回值会被忽略）
     */
    @Subscribe(channel = "test-subscribe-with-return")
    public String handleMessageWithReturn(String message) {
        LOGGER.info("Received message with return: {}", message);
        receivedMessages.add(message);
        processedCount.incrementAndGet();
        return "processed-" + message;
    }

    // Getter 方法用于测试验证
    public CopyOnWriteArrayList<String> getReceivedMessages() {
        return receivedMessages;
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    public ConcurrentHashMap<String, String> getMessageMap() {
        return messageMap;
    }

    // 清理方法，用于测试之间的清理
    public void clear() {
        receivedMessages.clear();
        processedCount.set(0);
        failedCount.set(0);
        messageMap.clear();
    }

}

