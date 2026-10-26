package com.pugwoo.wooutils.redis.impl;

import com.pugwoo.wooutils.redis.RedisHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Protocol;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Pub/Sub 发布订阅功能实现
 *
 * @author pugwoo
 */
public class RedisPubSub {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisPubSub.class);

    /**
     * 发布消息到指定的 channel
     *
     * @param redisHelper RedisHelper实例
     * @param channel 频道名称
     * @param message 消息内容
     * @return 接收到消息的订阅者数量，发送失败返回null
     */
    public static Long publish(RedisHelper redisHelper, String channel, String message) {
        if (channel == null || channel.trim().isEmpty()) {
            LOGGER.error("publish with error params: channel is null or empty");
            return null;
        }
        if (message == null) {
            LOGGER.error("publish with error params: message is null");
            return null;
        }

        try {
            return redisHelper.execute(jedis -> {
                try {
                    // 直接执行Redis命令: PUBLISH channel message
                    // 使用 sendCommand 可以避免不同版本 Jedis 的 API 差异
                    Object result = jedis.sendCommand(Protocol.Command.PUBLISH, channel, message);
                    if (result == null) {
                        return 0L;
                    }
                    if (result instanceof Long) {
                        return (Long) result;
                    } else if (result instanceof Integer) {
                        return ((Integer) result).longValue();
                    } else {
                        return Long.valueOf(result.toString());
                    }
                } catch (Exception e) {
                    LOGGER.error("publish error, channel:{}, message:{}", channel, message, e);
                    return null;
                }
            });
        } catch (Exception e) {
            LOGGER.error("publish error, channel:{}, message:{}", channel, message, e);
            return null;
        }
    }

    /**
     * 订阅指定的 channel，阻塞式接收一条消息后返回
     * 注意：此方法会阻塞当前线程，直到接收到一条消息或发生异常
     *
     * @param redisHelper RedisHelper实例
     * @param channel 要订阅的频道
     * @return 接收到的消息内容，如果发生异常返回null
     */
    public static String subscribe(RedisHelper redisHelper, String channel) {
        return subscribe(redisHelper, channel, 0);
    }

    /**
     * 订阅指定的 channel，最多阻塞 timeoutSec 秒接收一条消息后返回
     * <p>
     * 超时实现：Jedis 的 SUBSCRIBE 会永久阻塞在 socket 读上，靠 interrupt 无法唤醒，
     * 因此另起一条守护唤醒线程，等待超时后调用 jedisPubSub.unsubscribe() 发出取消订阅命令，
     * 让阻塞读正常读到取消确认并退出，连接仍是未订阅状态、可健康归还连接池。
     * 注意此处只能使用各版本 Jedis 都有的 JedisPubSub.unsubscribe()/isSubscribed()：
     * 本库需兼容 Jedis 3.1 等低版本（Jedis.getConnection() 是 4.x 才有的方法，
     * 在低版本上调用会抛 NoSuchMethodError 这种 Error、穿透 catch (Exception) 把调用线程打死）。
     *
     * @param redisHelper RedisHelper实例
     * @param channel 要订阅的频道
     * @param timeoutSec 最长等待秒数，小于等于0表示永久等待
     * @return 接收到的消息内容，超时或发生异常返回null
     */
    public static String subscribe(RedisHelper redisHelper, String channel, int timeoutSec) {
        if (channel == null || channel.trim().isEmpty()) {
            LOGGER.error("subscribe with error params: channel is null or empty");
            return null;
        }

        // 用于存储接收到的消息
        final String[] receivedMessage = new String[1];

        try {
            return redisHelper.execute(jedis -> {
                try {
                    // 是否已经发出过取消订阅：收到消息与等超时两个线程都有可能触发，只能有一个真正发出，
                    // 否则多余的 UNSUBSCRIBE 回复会串进连接池里下一个借用者的响应流
                    final AtomicBoolean unsubscribeInitiated = new AtomicBoolean(false);
                    // 本次 subscribe 调用是否已经结束
                    final AtomicBoolean finished = new AtomicBoolean(false);

                    JedisPubSub jedisPubSub = new JedisPubSub() {
                        @Override
                        public void onMessage(String ch, String message) {
                            try {
                                // 收到消息后保存
                                receivedMessage[0] = message;
                                // 取消订阅，这样 subscribe 方法会返回
                                if (unsubscribeInitiated.compareAndSet(false, true)) {
                                    this.unsubscribe();
                                }
                            } catch (Exception e) {
                                LOGGER.error("Error handling message, channel:{}, message:{}",
                                           ch, message, e);
                            }
                        }

                        @Override
                        public void onSubscribe(String ch, int subscribedChannels) {
                            LOGGER.debug("Subscribed to channel:{}", ch);
                        }

                        @Override
                        public void onUnsubscribe(String ch, int subscribedChannels) {
                            LOGGER.debug("Unsubscribed from channel:{}", ch);
                        }
                    };

                    Thread timeoutThread = null;
                    if (timeoutSec > 0) {
                        timeoutThread = new Thread(() -> {
                            try {
                                Thread.sleep(timeoutSec * 1000L);
                            } catch (InterruptedException e) {
                                return; // subscribe 已先返回（收到消息或出错），无需再唤醒
                            }
                            // 等超时后取消订阅来唤醒阻塞读；若订阅确认还没到，最多再等一个超时周期
                            long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
                            while (!finished.get() && System.currentTimeMillis() < deadline) {
                                if (jedisPubSub.isSubscribed()) {
                                    if (unsubscribeInitiated.compareAndSet(false, true)) {
                                        try {
                                            jedisPubSub.unsubscribe();
                                        } catch (Exception e) {
                                            LOGGER.debug("unsubscribe for subscribe timeout fail, channel:{}", channel, e);
                                        }
                                    }
                                    return;
                                }
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    return;
                                }
                            }
                        }, "RedisPubSub-subscribe-timeout");
                        timeoutThread.setDaemon(true);
                        timeoutThread.start();
                    }

                    // 直接在当前线程中订阅，会阻塞直到收到消息并取消订阅，或被唤醒线程取消订阅
                    try {
                        jedis.subscribe(jedisPubSub, channel);
                    } finally {
                        finished.set(true);
                        if (timeoutThread != null) {
                            timeoutThread.interrupt();
                        }
                    }

                    // subscribe 返回后，返回接收到的消息（超时取消订阅时为null）
                    return receivedMessage[0];
                } catch (Exception e) {
                    LOGGER.error("subscribe error, channel:{}", channel, e);
                    return null;
                }
            });
        } catch (Exception e) {
            LOGGER.error("subscribe error, channel:{}", channel, e);
            return null;
        }
    }
}

