package com.pugwoo.wooutils.redis;

import java.lang.annotation.*;

/**
 * 发送消息注解，注解在方法上。<br>
 * 当方法成功返回后，会自动发送消息到指定的topic。<br>
 * 功能等价于手工调用 redisHelper.send() 方法。<br>
 * <br>
 * 使用示例：<br>
 * <pre>
 * {@code
 * @SendMsg(topic = "order-topic", msgScript = "args[0].orderId + ',' + ret.status")
 * public OrderResult createOrder(OrderRequest request) {
 *     // 业务逻辑
 *     return orderResult;
 * }
 * }
 * </pre>
 * 
 * @author pugwoo
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(SendMsgs.class)
public @interface SendMsg {

    /**
     * [必须] 消息的topic，即redis的key
     */
    String topic();

    /**
     * [可选] 默认ack超时时间：当消费者消费了消息却没来得及设置ack超时时间时的默认超时秒数。<br>
     * 当消费者在这个时间内没有处理完，也即没有发送ack，那么消息将被重新投递。<br>
     * 默认值为3600秒（1小时）。<br>
     * 建议处理时间默认比较长的应用，可以将该值设置较大，例如60秒或120秒
     */
    int defaultAckTimeoutSec() default 3600;

    /**
     * [必须] 消息内容的mvel表达式脚本，可以从参数列表变量args和返回值变量ret中获取数据。<br>
     * <br>
     * 例如，注解在方法 OrderResult createOrder(OrderRequest request) 上，<br>
     * 那么可以设置 msgScript 为 "args[0].orderId + ',' + ret.status" 来构造消息内容。<br>
     * <br>
     * 可用变量：<br>
     * - args: 方法的参数数组，类型为Object[]<br>
     * - ret: 方法的返回值<br>
     * <br>
     * @return 【重要】如果脚本执行出错，则打log，并不会发送消息，也不会抛出异常阻止方法返回
     */
    String msgScript();

}

