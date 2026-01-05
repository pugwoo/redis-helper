package com.pugwoo.redishelpertest.common;

import com.pugwoo.redishelpertest.receivemsg.ReceiveMsgTestService;
import com.pugwoo.wooutils.redis.RedisHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * 测试 @ReceiveMsg 注解功能
 */
public abstract class TestReceiveMsg {

    public abstract RedisHelper getRedisHelper();

    public abstract ReceiveMsgTestService getReceiveMsgTestService();

    @BeforeEach
    public void setUp() {
        // 清理之前的测试数据
        getReceiveMsgTestService().clearReceivedMessages();
    }

    /**
     * 测试基本的消息接收功能
     */
    @Test
    public void testBasicReceiveMsg() throws InterruptedException {
        String topic = "test-receive-basic";
        String message = "test-message-" + UUID.randomUUID().toString();

        // 发送消息
        String uuid = getRedisHelper().send(topic, message);
        assert uuid != null;
        System.out.println("Sent message: " + message);

        // 等待消息被消费
        Thread.sleep(2000);

        // 验证消息已被接收和处理
        assert getReceiveMsgTestService().getReceivedMessages().contains(message);
        assert getReceiveMsgTestService().getProcessedCount() >= 1;

        System.out.println("testBasicReceiveMsg passed");
    }

    /**
     * 测试自定义 ackTimeout
     */
    @Test
    public void testReceiveMsgWithAckTimeout() throws InterruptedException {
        String topic = "test-receive-ack-timeout";
        String message = "test-ack-timeout-" + UUID.randomUUID().toString();

        // 发送消息
        String uuid = getRedisHelper().send(topic, message, 60);
        assert uuid != null;

        // 等待消息被消费
        Thread.sleep(2000);

        // 验证消息已被接收
        assert getReceiveMsgTestService().getReceivedMessages().contains(message);

        System.out.println("testReceiveMsgWithAckTimeout passed");
    }

    /**
     * 测试多线程消费
     */
    @Test
    public void testReceiveMsgWithMultiThread() throws InterruptedException {
        String topic = "test-receive-multi-thread";
        int messageCount = 10;

        // 发送多条消息
        for (int i = 0; i < messageCount; i++) {
            String message = "multi-thread-msg-" + i;
            getRedisHelper().send(topic, message);
        }

        // 等待消息被消费
        Thread.sleep(5000);

        // 验证所有消息都被接收
        assert getReceiveMsgTestService().getProcessedCount() >= messageCount;

        System.out.println("testReceiveMsgWithMultiThread passed, processed: " 
                + getReceiveMsgTestService().getProcessedCount());
    }

    /**
     * 测试处理异常的情况（应该自动 nack）
     */
    @Test
    public void testReceiveMsgWithException() throws InterruptedException {
        String topic = "test-receive-exception";
        String message = "exception-msg-" + UUID.randomUUID().toString();

        try {
            // 发送消息
            String uuid = getRedisHelper().send(topic, message, 5); // 5秒超时，方便测试

            // 等待消息被消费（会失败并 nack）
            Thread.sleep(2000);

            // 验证失败计数增加
            assert getReceiveMsgTestService().getFailedCount() >= 1;

            // 等待消息重新投递
            Thread.sleep(6000);

            // 验证消息被重新消费（再次失败）
            assert getReceiveMsgTestService().getFailedCount() >= 2;

            System.out.println("testReceiveMsgWithException passed, failed count: "
                    + getReceiveMsgTestService().getFailedCount());
        } finally {
            // 清理测试产生的异常消息，避免在Redis中堆积
            getRedisHelper().removeTopic(topic);
            System.out.println("Cleaned up exception messages for topic: " + topic);
        }
    }

    /**
     * 测试慢消息处理
     */
    @Test
    public void testReceiveMsgWithSlowProcessing() throws InterruptedException {
        String topic = "test-receive-slow";
        String message = "slow-msg-" + UUID.randomUUID().toString();

        // 发送消息
        String uuid = getRedisHelper().send(topic, message);
        assert uuid != null;

        // 等待消息被消费（需要2秒处理时间）
        Thread.sleep(4000);

        // 验证消息已被接收
        assert getReceiveMsgTestService().getReceivedMessages().contains(message);

        System.out.println("testReceiveMsgWithSlowProcessing passed");
    }

    /**
     * 测试多个 topic
     */
    @Test
    public void testReceiveMsgWithMultiTopic() throws InterruptedException {
        String topic1 = "test-receive-multi-topic-1";
        String topic2 = "test-receive-multi-topic-2";
        String message1 = "topic1-msg-" + UUID.randomUUID().toString();
        String message2 = "topic2-msg-" + UUID.randomUUID().toString();

        // 发送消息到两个 topic
        String uuid1 = getRedisHelper().send(topic1, message1);
        String uuid2 = getRedisHelper().send(topic2, message2);
        assert uuid1 != null && uuid2 != null;

        // 等待消息被消费
        Thread.sleep(3000);

        // 验证两个消息都被接收
        assert getReceiveMsgTestService().getMessageMap().containsValue(message1);
        assert getReceiveMsgTestService().getMessageMap().containsValue(message2);

        System.out.println("testReceiveMsgWithMultiTopic passed");
    }

}

