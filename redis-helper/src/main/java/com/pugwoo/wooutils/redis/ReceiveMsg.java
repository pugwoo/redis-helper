package com.pugwoo.wooutils.redis;

import java.lang.annotation.*;

/**
 * 接收消息注解，注解在方法上。<br>
 * 该注解会启动独立的线程来阻塞等待消息，当收到消息后，会将 RedisMsg 实例传入方法的 RedisMsg 类型的参数中。<br>
 * 【重要】：该方法必须有且只有一个参数，且参数类型为RedisMsg<br>
 * 当处理方法处理完成后，自动调用 ack；如果抛出异常，则调用 nack。<br>
 * 功能等价于手工调用 redisHelper.receive() 方法。<br>
 * <br>
 * 使用示例：<br>
 * <pre>
 * {@code
 * @ReceiveMsg(topic = "order-topic")
 * public void handleOrder(RedisMsg msg) {
 *     String content = msg.getMsg();
 *     // 处理消息逻辑
 * }
 * }
 * </pre>
 * 
 * @author pugwoo
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ReceiveMsgs.class)
public @interface ReceiveMsg {

    /**
     * [必须] 消息的topic，即redis的key
     */
    String topic();

    /**
     * [可选] ack确认超时的秒数，设置为-1则表示不修改，使用发送方设置的默认超时值。<br>
     * 如果设置了具体值，则会覆盖发送方设置的超时时间。
     */
    int ackTimeoutSec() default -1;

    /**
     * [可选] 消费者线程数量，默认为1。<br>
     * 设置多个线程可以并发消费消息，提高消费速度。<br>
     * 【重要】设置的并发线程数将阻塞占用redisHelper处理接收消息的线程池（最大200个线程，也可自行指定）<br>
     * 注意：分布式环境下或多线程消费时，消息的顺序无法保证。
     */
    int consumerThreads() default 1;

}

