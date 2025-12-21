package com.pugwoo.redishelpertest.sendmsg;

import com.pugwoo.wooutils.redis.SendMsg;
import com.pugwoo.wooutils.redis.SendMsgs;

/**
 * 测试 @SendMsg 注解的服务类
 */
public class SendMsgTestService {

    /**
     * 测试基本的消息发送功能
     */
    @SendMsg(topic = "test-topic-basic", msgScript = "'order-' + args[0]")
    public String createOrder(String orderId) {
        return "success-" + orderId;
    }

    /**
     * 测试使用返回值的消息发送
     */
    @SendMsg(topic = "test-topic-with-ret", msgScript = "args[0] + ':' + ret", defaultAckTimeoutSec = 60)
    public String processOrder(String orderId) {
        return "processed";
    }

    /**
     * 测试复杂对象的消息发送
     */
    @SendMsg(topic = "test-topic-complex", msgScript = "args[0].orderId + ',' + args[0].amount + ',' + ret.status")
    public OrderResult createComplexOrder(OrderRequest request) {
        OrderResult result = new OrderResult();
        result.setOrderId(request.getOrderId());
        result.setStatus("SUCCESS");
        result.setMessage("Order created successfully");
        return result;
    }

    /**
     * 测试多个 @SendMsg 注解
     */
    @SendMsgs({
        @SendMsg(topic = "test-topic-multi-1", msgScript = "'topic1-' + args[0]"),
        @SendMsg(topic = "test-topic-multi-2", msgScript = "'topic2-' + args[0] + '-' + ret", defaultAckTimeoutSec = 120)
    })
    public String multiTopicOrder(String orderId) {
        return "multi-" + orderId;
    }

    /**
     * 测试返回 null 的情况
     */
    @SendMsg(topic = "test-topic-null-ret", msgScript = "args[0] + ':' + ret")
    public String nullReturnMethod(String input) {
        return null;
    }

    /**
     * 测试方法抛出异常的情况（不应该发送消息）
     */
    @SendMsg(topic = "test-topic-exception", msgScript = "args[0]")
    public String exceptionMethod(String input) {
        throw new RuntimeException("Test exception");
    }

    /**
     * 测试 msgScript 执行失败的情况
     */
    @SendMsg(topic = "test-topic-script-error", msgScript = "args[0].nonExistentField")
    public String scriptErrorMethod(String input) {
        return "result";
    }

    /**
     * 订单请求对象
     */
    public static class OrderRequest {
        private String orderId;
        private double amount;

        public OrderRequest() {
        }

        public OrderRequest(String orderId, double amount) {
            this.orderId = orderId;
            this.amount = amount;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }
    }

    /**
     * 订单结果对象
     */
    public static class OrderResult {
        private String orderId;
        private String status;
        private String message;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

}

