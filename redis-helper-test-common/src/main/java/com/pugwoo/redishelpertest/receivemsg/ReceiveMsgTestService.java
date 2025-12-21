package com.pugwoo.redishelpertest.receivemsg;

import com.pugwoo.wooutils.redis.ReceiveMsg;
import com.pugwoo.wooutils.redis.ReceiveMsgs;
import com.pugwoo.wooutils.redis.RedisMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试 @ReceiveMsg 注解的服务类
 * 注意：这个类需要在具体的测试项目中通过 @Service 或 @Bean 注册为 Spring Bean
 */
public class ReceiveMsgTestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveMsgTestService.class);

    // 用于测试验证的数据结构
    private final CopyOnWriteArrayList<String> receivedMessages = new CopyOnWriteArrayList<>();
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, String> messageMap = new ConcurrentHashMap<>();

    /**
     * 测试基本的消息接收功能
     */
    @ReceiveMsg(topic = "test-receive-basic")
    public void handleBasicMessage(RedisMsg msg) {
        LOGGER.info("Received basic message: {}", msg.getMsg());
        receivedMessages.add(msg.getMsg());
        processedCount.incrementAndGet();
    }

    /**
     * 测试自定义 ackTimeout
     */
    @ReceiveMsg(topic = "test-receive-ack-timeout", ackTimeoutSec = 30)
    public void handleMessageWithAckTimeout(RedisMsg msg) {
        LOGGER.info("Received message with ack timeout: {}", msg.getMsg());
        receivedMessages.add(msg.getMsg());
        processedCount.incrementAndGet();
    }

    /**
     * 测试多线程消费
     */
    @ReceiveMsg(topic = "test-receive-multi-thread", consumerThreads = 3)
    public void handleMessageWithMultiThread(RedisMsg msg) {
        LOGGER.info("Received message in multi-thread mode: {} by thread {}", 
                msg.getMsg(), Thread.currentThread().getName());
        receivedMessages.add(msg.getMsg());
        processedCount.incrementAndGet();
    }

    /**
     * 测试处理异常的情况（应该自动 nack）
     */
    @ReceiveMsg(topic = "test-receive-exception")
    public void handleMessageWithException(RedisMsg msg) {
        LOGGER.info("Received message that will throw exception: {}", msg.getMsg());
        failedCount.incrementAndGet();
        throw new RuntimeException("Test exception for message: " + msg.getMsg());
    }

    /**
     * 测试处理慢消息
     */
    @ReceiveMsg(topic = "test-receive-slow", ackTimeoutSec = 60)
    public void handleSlowMessage(RedisMsg msg) throws InterruptedException {
        LOGGER.info("Received slow message: {}", msg.getMsg());
        Thread.sleep(2000); // 模拟慢处理
        receivedMessages.add(msg.getMsg());
        processedCount.incrementAndGet();
    }

    /**
     * 测试多个 @ReceiveMsg 注解（同一个方法处理多个 topic）
     */
    @ReceiveMsgs({
        @ReceiveMsg(topic = "test-receive-multi-topic-1"),
        @ReceiveMsg(topic = "test-receive-multi-topic-2")
    })
    public void handleMultiTopicMessage(RedisMsg msg) {
        LOGGER.info("Received message from multi-topic: {}", msg.getMsg());
        messageMap.put(msg.getUuid(), msg.getMsg());
        processedCount.incrementAndGet();
    }

    /**
     * 测试消息内容处理
     */
    @ReceiveMsg(topic = "test-receive-content-processing")
    public void handleContentProcessing(RedisMsg msg) {
        String content = msg.getMsg();
        LOGGER.info("Processing message content: {}", content);
        
        // 模拟业务处理
        String[] parts = content.split(",");
        if (parts.length >= 2) {
            String key = parts[0];
            String value = parts[1];
            messageMap.put(key, value);
        }
        
        receivedMessages.add(content);
        processedCount.incrementAndGet();
    }

    // 测试辅助方法

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

    public void clearReceivedMessages() {
        receivedMessages.clear();
        processedCount.set(0);
        failedCount.set(0);
        messageMap.clear();
    }

}

