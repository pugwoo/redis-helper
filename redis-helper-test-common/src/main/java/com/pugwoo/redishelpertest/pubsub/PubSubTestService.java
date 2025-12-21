package com.pugwoo.redishelpertest.pubsub;

import com.pugwoo.wooutils.redis.Publish;
import com.pugwoo.wooutils.redis.Publishs;

/**
 * 测试 @Publish 注解的服务类
 */
public class PubSubTestService {

    /**
     * 测试基本的消息发布功能
     */
    @Publish(channel = "test-channel-basic", msgScript = "'order-' + args[0]")
    public String createOrder(String orderId) {
        return "success-" + orderId;
    }

    /**
     * 测试使用返回值的消息发布
     */
    @Publish(channel = "test-channel-with-ret", msgScript = "args[0] + ':' + ret")
    public String processOrder(String orderId) {
        return "processed";
    }

    /**
     * 测试复杂对象的消息发布
     */
    @Publish(channel = "test-channel-complex", msgScript = "args[0].orderId + ',' + args[0].amount + ',' + ret.status")
    public OrderResult createComplexOrder(OrderRequest request) {
        OrderResult result = new OrderResult();
        result.setOrderId(request.getOrderId());
        result.setStatus("SUCCESS");
        result.setMessage("Order created successfully");
        return result;
    }

    /**
     * 测试多个 @Publish 注解
     */
    @Publishs({
        @Publish(channel = "test-channel-multi-1", msgScript = "'channel1-' + args[0]"),
        @Publish(channel = "test-channel-multi-2", msgScript = "'channel2-' + args[0] + '-' + ret")
    })
    public String multiChannelOrder(String orderId) {
        return "multi-" + orderId;
    }

    /**
     * 测试返回 null 的情况
     */
    @Publish(channel = "test-channel-null-ret", msgScript = "args[0] + ':' + ret")
    public String nullReturnMethod(String input) {
        return null;
    }

    /**
     * 测试方法抛出异常的情况（不应该发布消息）
     */
    @Publish(channel = "test-channel-exception", msgScript = "args[0]")
    public String exceptionMethod(String input) {
        throw new RuntimeException("Test exception");
    }

    /**
     * 测试 msgScript 执行失败的情况
     */
    @Publish(channel = "test-channel-script-error", msgScript = "args[0].nonExistentField")
    public String scriptErrorMethod(String input) {
        return "result";
    }

    // 测试用的数据类
    public static class OrderRequest {
        private String orderId;
        private double amount;

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

