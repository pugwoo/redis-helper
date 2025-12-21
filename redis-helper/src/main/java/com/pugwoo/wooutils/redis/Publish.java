package com.pugwoo.wooutils.redis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

/**
 * 发布消息注解，注解在方法上。<br>
 * 当方法成功返回后，会自动发布消息到指定的 channel。<br>
 * 功能等价于手工调用 redisHelper.publish() 方法。<br>
 * <br>
 * 使用示例：<br>
 * <pre>
 * {@code
 * @Publish(channel = "order-channel", msgScript = "args[0].orderId + ',' + ret.status")
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
@Repeatable(Publishs.class)
public @interface Publish {

    /**
     * [必须] 消息的 channel，即 Redis Pub/Sub 的频道名称
     */
    String channel();

    /**
     * [必须] MVEL 表达式，用于构造消息内容，可以从以下变量获取数据：<br>
     * - args: 方法的参数数组（Object[]）<br>
     * - ret: 方法的返回值<br>
     * <br>
     * 示例：<br>
     * - "'order-' + args[0]" - 使用第一个参数<br>
     * - "args[0] + ':' + ret" - 组合参数和返回值<br>
     * - "args[0].orderId + ',' + ret.status" - 访问对象属性<br>
     */
    String msgScript();

}

