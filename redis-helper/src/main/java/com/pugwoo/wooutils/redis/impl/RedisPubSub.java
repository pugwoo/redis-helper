package com.pugwoo.wooutils.redis.impl;

import com.pugwoo.wooutils.redis.RedisHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Protocol;

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
        if (channel == null || channel.trim().isEmpty()) {
            LOGGER.error("subscribe with error params: channel is null or empty");
            return null;
        }

        // 用于存储接收到的消息
        final String[] receivedMessage = new String[1];

        try {
            return redisHelper.execute(jedis -> {
                try {
                    JedisPubSub jedisPubSub = new JedisPubSub() {
                        @Override
                        public void onMessage(String ch, String message) {
                            try {
                                // 收到消息后保存
                                receivedMessage[0] = message;
                                // 取消订阅，这样 subscribe 方法会返回
                                this.unsubscribe();
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

                    // 直接在当前线程中订阅，会阻塞直到收到消息并取消订阅
                    jedis.subscribe(jedisPubSub, channel);

                    // subscribe 返回后，返回接收到的消息
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

