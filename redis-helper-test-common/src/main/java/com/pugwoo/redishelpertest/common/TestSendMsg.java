package com.pugwoo.redishelpertest.common;

import com.pugwoo.redishelpertest.sendmsg.SendMsgTestService;
import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.RedisMsg;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * 测试 @SendMsg 注解功能
 */
public abstract class TestSendMsg {

    public abstract RedisHelper getRedisHelper();

    public abstract SendMsgTestService getSendMsgTestService();

    /**
     * 测试基本的消息发送功能
     */
    @Test
    public void testBasicSendMsg() {
        String orderId = UUID.randomUUID().toString();
        String result = getSendMsgTestService().createOrder(orderId);
        
        assert result.equals("success-" + orderId);
        
        // 接收消息验证
        RedisMsg msg = getRedisHelper().receive("test-topic-basic", 5, null);
        assert msg != null;
        assert msg.getMsg().equals("order-" + orderId);
        
        // 确认消息
        assert getRedisHelper().ack("test-topic-basic", msg.getUuid());
        
        System.out.println("testBasicSendMsg passed, msg: " + msg.getMsg());
    }

    /**
     * 测试使用返回值的消息发送
     */
    @Test
    public void testSendMsgWithReturnValue() {
        String orderId = UUID.randomUUID().toString();
        String result = getSendMsgTestService().processOrder(orderId);
        
        assert result.equals("processed");
        
        // 接收消息验证
        RedisMsg msg = getRedisHelper().receive("test-topic-with-ret", 5, null);
        assert msg != null;
        assert msg.getMsg().equals(orderId + ":processed");
        assert msg.getAckTimeout() == 60; // 验证自定义的超时时间
        
        // 确认消息
        assert getRedisHelper().ack("test-topic-with-ret", msg.getUuid());
        
        System.out.println("testSendMsgWithReturnValue passed, msg: " + msg.getMsg());
    }

    /**
     * 测试复杂对象的消息发送
     */
    @Test
    public void testSendMsgWithComplexObject() {
        SendMsgTestService.OrderRequest request = new SendMsgTestService.OrderRequest(
            UUID.randomUUID().toString(), 
            99.99
        );
        
        SendMsgTestService.OrderResult result = getSendMsgTestService().createComplexOrder(request);
        
        assert result.getOrderId().equals(request.getOrderId());
        assert result.getStatus().equals("SUCCESS");
        
        // 接收消息验证
        RedisMsg msg = getRedisHelper().receive("test-topic-complex", 5, null);
        assert msg != null;
        assert msg.getMsg().equals(request.getOrderId() + ",99.99,SUCCESS");
        
        // 确认消息
        assert getRedisHelper().ack("test-topic-complex", msg.getUuid());
        
        System.out.println("testSendMsgWithComplexObject passed, msg: " + msg.getMsg());
    }

    /**
     * 测试多个 @SendMsg 注解
     */
    @Test
    public void testMultipleSendMsg() {
        String orderId = UUID.randomUUID().toString();
        String result = getSendMsgTestService().multiTopicOrder(orderId);
        
        assert result.equals("multi-" + orderId);
        
        // 接收第一个topic的消息
        RedisMsg msg1 = getRedisHelper().receive("test-topic-multi-1", 5, null);
        assert msg1 != null;
        assert msg1.getMsg().equals("topic1-" + orderId);
        assert getRedisHelper().ack("test-topic-multi-1", msg1.getUuid());
        
        // 接收第二个topic的消息
        RedisMsg msg2 = getRedisHelper().receive("test-topic-multi-2", 5, null);
        assert msg2 != null;
        assert msg2.getMsg().equals("topic2-" + orderId + "-multi-" + orderId);
        assert msg2.getAckTimeout() == 120; // 验证自定义的超时时间
        assert getRedisHelper().ack("test-topic-multi-2", msg2.getUuid());
        
        System.out.println("testMultipleSendMsg passed, msg1: " + msg1.getMsg() + ", msg2: " + msg2.getMsg());
    }

    /**
     * 测试返回 null 的情况
     */
    @Test
    public void testSendMsgWithNullReturn() {
        String input = UUID.randomUUID().toString();
        String result = getSendMsgTestService().nullReturnMethod(input);
        
        assert result == null;
        
        // 接收消息验证（返回null时，msgScript会得到"null"字符串）
        RedisMsg msg = getRedisHelper().receive("test-topic-null-ret", 5, null);
        assert msg != null;
        assert msg.getMsg().equals(input + ":null");
        
        // 确认消息
        assert getRedisHelper().ack("test-topic-null-ret", msg.getUuid());
        
        System.out.println("testSendMsgWithNullReturn passed, msg: " + msg.getMsg());
    }

    /**
     * 测试方法抛出异常的情况（不应该发送消息）
     */
    @Test
    public void testSendMsgWithException() {
        String input = UUID.randomUUID().toString();
        
        try {
            getSendMsgTestService().exceptionMethod(input);
            assert false : "Should throw exception";
        } catch (RuntimeException e) {
            assert e.getMessage().equals("Test exception");
        }
        
        // 不应该接收到消息
        RedisMsg msg = getRedisHelper().receive("test-topic-exception", 2, null);
        assert msg == null : "Should not receive message when method throws exception";
        
        System.out.println("testSendMsgWithException passed, no message sent");
    }

}

