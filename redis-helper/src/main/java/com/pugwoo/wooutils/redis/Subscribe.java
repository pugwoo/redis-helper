package com.pugwoo.wooutils.redis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

/**
 * 订阅消息注解，注解在方法上。<br>
 * 该注解会启动独立的线程来阻塞等待消息，当收到消息后，会将消息内容（String）传入方法的参数中。<br>
 * 【重要】：该方法必须有且只有一个参数，且参数类型为 String<br>
 * 功能等价于手工调用 redisHelper.subscribe() 方法。<br>
 * <br>
 * 使用示例：<br>
 * <pre>
 * {@code
 * @Subscribe(channel = "order-channel")
 * public void handleOrder(String message) {
 *     // 处理消息逻辑
 * }
 * }
 * </pre>
 * 
 * @author pugwoo
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Subscribes.class)
public @interface Subscribe {

    /**
     * [必须] 消息的 channel，即 Redis Pub/Sub 的频道名称
     */
    String channel();

    /**
     * [可选] 消费者线程数量，默认为 1。<br>
     * 如果需要并发处理同一个 channel 的消息，可以设置多个消费者线程。<br>
     * 注意：Redis Pub/Sub 是广播模式，每个订阅者都会收到所有消息，
     * 多个线程订阅同一个 channel 会导致每条消息被处理多次。
     */
    int consumerThreads() default 1;

}

