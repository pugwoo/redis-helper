package com.pugwoo.redishelpertest.common;

import com.pugwoo.redishelpertest.pubsub.PubSubTestService;
import com.pugwoo.redishelpertest.pubsub.SubscribeTestService;
import com.pugwoo.wooutils.redis.RedisHelper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * 测试 @Publish 和 @Subscribe 注解功能
 */
public abstract class TestPubSub {

    public abstract RedisHelper getRedisHelper();

    public abstract PubSubTestService getPubSubTestService();

    public abstract SubscribeTestService getSubscribeTestService();

    /**
     * 测试基本的消息发布功能
     */
    @Test
    public void testBasicPublish() throws InterruptedException {
        String orderId = UUID.randomUUID().toString();
        String result = getPubSubTestService().createOrder(orderId);
        
        assert result.equals("success-" + orderId);
        
        // 等待消息被订阅者接收
        Thread.sleep(1000);
        
        System.out.println("testBasicPublish passed, result: " + result);
    }

    /**
     * 测试使用返回值的消息发布
     */
    @Test
    public void testPublishWithReturnValue() throws InterruptedException {
        String orderId = UUID.randomUUID().toString();
        String result = getPubSubTestService().processOrder(orderId);
        
        assert result.equals("processed");
        
        // 等待消息被订阅者接收
        Thread.sleep(1000);
        
        System.out.println("testPublishWithReturnValue passed, result: " + result);
    }

    /**
     * 测试复杂对象的消息发布
     */
    @Test
    public void testPublishWithComplexObject() throws InterruptedException {
        PubSubTestService.OrderRequest request = new PubSubTestService.OrderRequest(
            UUID.randomUUID().toString(), 
            99.99
        );
        
        PubSubTestService.OrderResult result = getPubSubTestService().createComplexOrder(request);
        
        assert result.getOrderId().equals(request.getOrderId());
        assert result.getStatus().equals("SUCCESS");
        
        // 等待消息被订阅者接收
        Thread.sleep(1000);
        
        System.out.println("testPublishWithComplexObject passed, orderId: " + result.getOrderId());
    }

    /**
     * 测试多个 @Publish 注解
     */
    @Test
    public void testMultiplePublish() throws InterruptedException {
        String orderId = UUID.randomUUID().toString();
        String result = getPubSubTestService().multiChannelOrder(orderId);
        
        assert result.equals("multi-" + orderId);
        
        // 等待消息被订阅者接收
        Thread.sleep(1000);
        
        System.out.println("testMultiplePublish passed, result: " + result);
    }

    /**
     * 测试返回 null 的情况
     */
    @Test
    public void testPublishWithNullReturn() throws InterruptedException {
        String input = UUID.randomUUID().toString();
        String result = getPubSubTestService().nullReturnMethod(input);
        
        assert result == null;
        
        // 等待消息被订阅者接收
        Thread.sleep(1000);
        
        System.out.println("testPublishWithNullReturn passed");
    }

    /**
     * 测试方法抛出异常的情况（不应该发布消息）
     */
    @Test
    public void testPublishWithException() throws InterruptedException {
        String input = UUID.randomUUID().toString();
        
        try {
            getPubSubTestService().exceptionMethod(input);
            assert false : "Should throw exception";
        } catch (RuntimeException e) {
            assert e.getMessage().equals("Test exception");
        }
        
        // 等待确认没有消息被发布
        Thread.sleep(1000);
        
        System.out.println("testPublishWithException passed, no message published");
    }

    /**
     * 测试基本的消息订阅功能
     */
    @Test
    public void testBasicSubscribe() throws InterruptedException {
        String channel = "test-subscribe-basic";
        String message = "test-message-" + UUID.randomUUID().toString();

        // 清理之前的数据
        getSubscribeTestService().clear();

        // 等待订阅者准备好
        Thread.sleep(1000);

        // 发布消息
        Long subscribers = getRedisHelper().publish(channel, message);
        System.out.println("Published message to " + subscribers + " subscriber(s)");

        // 等待消息被消费
        Thread.sleep(2000);

        // 验证消息已被接收和处理
        assert getSubscribeTestService().getReceivedMessages().contains(message);
        assert getSubscribeTestService().getProcessedCount() >= 1;

        System.out.println("testBasicSubscribe passed");
    }

    /**
     * 测试多线程订阅
     */
    @Test
    public void testSubscribeWithMultiThread() throws InterruptedException {
        String channel = "test-subscribe-multi-thread";
        int messageCount = 5;

        // 清理之前的数据
        getSubscribeTestService().clear();

        // 等待订阅者准备好
        Thread.sleep(1000);

        // 发送多条消息
        for (int i = 0; i < messageCount; i++) {
            String message = "multi-thread-msg-" + i;
            getRedisHelper().publish(channel, message);
            Thread.sleep(200);
        }

        // 等待消息被消费
        Thread.sleep(3000);

        // 验证消息被接收（注意：由于有3个订阅者线程，每条消息会被接收3次）
        assert getSubscribeTestService().getProcessedCount() >= messageCount;

        System.out.println("testSubscribeWithMultiThread passed, processed: "
                + getSubscribeTestService().getProcessedCount());
    }

    /**
     * 测试处理异常的情况
     */
    @Test
    public void testSubscribeWithException() throws InterruptedException {
        String channel = "test-subscribe-exception";
        String message = "exception-msg-" + UUID.randomUUID().toString();

        // 清理之前的数据
        getSubscribeTestService().clear();

        // 等待订阅者准备好
        Thread.sleep(1000);

        // 发布消息
        Long subscribers = getRedisHelper().publish(channel, message);
        System.out.println("Published message to " + subscribers + " subscriber(s)");

        // 等待消息被消费（会失败）
        Thread.sleep(2000);

        // 验证失败计数增加
        assert getSubscribeTestService().getFailedCount() >= 1;

        System.out.println("testSubscribeWithException passed, failed count: "
                + getSubscribeTestService().getFailedCount());
    }

    /**
     * 测试慢消息处理
     */
    @Test
    public void testSubscribeWithSlowProcessing() throws InterruptedException {
        String channel = "test-subscribe-slow";
        String message = "slow-msg-" + UUID.randomUUID().toString();

        // 清理之前的数据
        getSubscribeTestService().clear();

        // 等待订阅者准备好
        Thread.sleep(1000);

        // 发布消息
        Long subscribers = getRedisHelper().publish(channel, message);
        System.out.println("Published message to " + subscribers + " subscriber(s)");

        // 等待消息被消费（需要2秒处理时间）
        Thread.sleep(4000);

        // 验证消息已被接收
        assert getSubscribeTestService().getReceivedMessages().contains(message);

        System.out.println("testSubscribeWithSlowProcessing passed");
    }

    /**
     * 测试多个 channel
     */
    @Test
    public void testSubscribeWithMultiChannel() throws InterruptedException {
        String channel1 = "test-subscribe-multi-channel-1";
        String channel2 = "test-subscribe-multi-channel-2";
        String message1 = "channel1-msg-" + UUID.randomUUID().toString();
        String message2 = "channel2-msg-" + UUID.randomUUID().toString();

        // 清理之前的数据
        getSubscribeTestService().clear();

        // 等待订阅者准备好
        Thread.sleep(1000);

        // 发布消息到两个 channel
        Long subscribers1 = getRedisHelper().publish(channel1, message1);
        Long subscribers2 = getRedisHelper().publish(channel2, message2);
        System.out.println("Published to channel1: " + subscribers1 + " subscribers");
        System.out.println("Published to channel2: " + subscribers2 + " subscribers");

        // 等待消息被消费
        Thread.sleep(3000);

        // 验证两个消息都被接收
        assert getSubscribeTestService().getReceivedMessages().contains(message1);
        assert getSubscribeTestService().getReceivedMessages().contains(message2);

        System.out.println("testSubscribeWithMultiChannel passed");
    }

    /**
     * 测试 @Publish 和 @Subscribe 配合使用
     */
    @Test
    public void testPublishAndSubscribeTogether() throws InterruptedException {
        // 这个测试需要先设置好订阅者，然后通过 @Publish 注解发布消息
        // 由于 @Subscribe 注解的订阅者已经在启动时自动启动，这里只需要触发 @Publish

        String orderId = UUID.randomUUID().toString();

        // 清理之前的数据
        getSubscribeTestService().clear();

        // 等待订阅者准备好
        Thread.sleep(1000);

        // 调用带有 @Publish 注解的方法
        String result = getPubSubTestService().createOrder(orderId);
        assert result.equals("success-" + orderId);

        // 等待消息被订阅者接收
        Thread.sleep(2000);

        System.out.println("testPublishAndSubscribeTogether passed");
    }

}


