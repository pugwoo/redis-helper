package com.pugwoo.wooutils.redis.impl;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.RedisMsg;
import com.pugwoo.wooutils.redis.RedisQueueStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.toList;

/**
 * 基于redis实现的带ack机制的消息队列
 * @author nick
 */
public class RedisMsgQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMsgQueue.class);
    private static final String REDIS_MSG_QUEUE_TOPICS_KEY = "_RedisMsgQueueTopics_";
    /**默认消息的确认超时时间，当消费者超过这个时间不确认，消息将回到等待队列重新投递*/
    private static final int defaultActTimeoutSec = 3600;

    private static String getPendingKey(String topic) {
        return "{" + topic + "}:" + "MQLIST"; // 特别说明：使用redis hash tag来使得key落到同一个分片
    }

    private static String getDoingKey(String topic) {
        return "{" + topic + "}:" + "MQDOING"; // 特别说明：使用redis hash tag来使得key落到同一个分片
    }

    private static String getMapKey(String topic) {
        return "{" + topic + "}:" + "MQMSG"; // 特别说明：使用redis hash tag来使得key落到同一个分片
    }

    /** 生成一个消息uuid */
    private static String getMsgUuid() {
        return "rmq" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 发送消息，返回消息的uuid。默认的超时时间是86400秒
     * @param redisHelper
     * @param topic topic将是redis的key
     * @param msg
     * @return
     */
    public static String send(RedisHelper redisHelper, String topic, String msg) {
        return send(redisHelper, topic, msg, defaultActTimeoutSec);
    }

    /**
     * 发送消息，返回消息的uuid
     * @param redisHelper
     * @param topic 即redis的key
     * @param msg
     * @param defaultAckTimeoutSec 默认ack超时时间：当消费者消费了消息却没来得及设置ack超时时间时的默认超时秒数。建议处理时间默认比较长的应用，可以将该值设置较大，例如60秒或120秒
     * @return 消息的uuid，发送失败返回null
     */
    public static String send(RedisHelper redisHelper, String topic, String msg, int defaultAckTimeoutSec) {

        String uuid = getMsgUuid();

        RedisMsg redisMsg = new RedisMsg();
        redisMsg.setUuid(uuid);
        redisMsg.setMsg(msg);
        redisMsg.setSendTime(System.currentTimeMillis());
        redisMsg.setAckTimeout(defaultAckTimeoutSec);

        String listKey = getPendingKey(topic);
        String mapKey = getMapKey(topic);

        List<Object> result = redisHelper.executePipeline(pipeline -> {
            pipeline.hset(mapKey, uuid, JsonRedisObjectConverter.toJson(redisMsg));
            pipeline.lpush(listKey, uuid);
        });

        boolean success = true;
        if(result != null && result.size() == 2) {
            if(!(result.get(0)!=null && (result.get(0) instanceof Long) && result.get(0).equals(1L))) {
                success = false;
                LOGGER.error("send msg:{}, content:{} fail, redis result[0] != 1", uuid, msg);
            }
            if(!(result.get(0)!=null && (result.get(0) instanceof Long) && ((Long)result.get(0)) > 0L)) {
                success = false;
                LOGGER.error("send msg:{}, content:{} fail, redis result[1] < 1", uuid, msg);
            }
        } else {
            LOGGER.error("send msg:{}, content:{} fail, redis result size != 2",
                    uuid, msg);
            success = false;
        }

        return success ? uuid : null;
    }
    
    /**
     * 发送消息，返回消息的uuidList。默认的超时时间是86400秒
     * @param redisHelper
     * @param topic topic将是redis的key
     * @param msgList 消息内容列表
     * @return 消息的uuidList，发送失败返回null
     */
    public static List<String> sendBatch(RedisHelper redisHelper, String topic, List<String> msgList) {
        return sendBatch(redisHelper, topic, msgList, defaultActTimeoutSec);
    }
    
    /**
     * 发送消息，返回消息的uuidList。
     * @param redisHelper
     * @param topic   topic将是redis的key
     * @param msgList 消息内容列表
     * @param defaultAckTimeoutSec 默认ack超时时间：当消费者消费了消息却没来得及设置ack超时时间时的默认超时秒数。
     * @return 消息的uuidList，发送失败返回null
     */
    public static List<String> sendBatch(RedisHelper redisHelper, String topic, List<String> msgList, int defaultAckTimeoutSec) {
        if (msgList == null || msgList.isEmpty()) { return new ArrayList<>(); }
        
        long sentTime = System.currentTimeMillis();
        List<RedisMsg> redisMsgList = msgList.stream()
                .map(item -> {
                    RedisMsg redisMsg = new RedisMsg();
                    redisMsg.setUuid(getMsgUuid());
                    redisMsg.setMsg(item);
                    redisMsg.setSendTime(sentTime);
                    redisMsg.setAckTimeout(defaultAckTimeoutSec);
                    return redisMsg;
                })
                .collect(toList());
        
        String pendingKey = getPendingKey(topic);
        String mapKey = getMapKey(topic);
        List<Object> sendResultList = redisHelper.executePipeline(pipeline -> {
            for (RedisMsg redisMsg : redisMsgList) {
                pipeline.hset(mapKey, redisMsg.getUuid(), JsonRedisObjectConverter.toJson(redisMsg));
                pipeline.lpush(pendingKey, redisMsg.getUuid());
            }
        });
        
        List<String> resultList = new ArrayList<>();
        int sendResultSize = sendResultList == null ? 0 : sendResultList.size();
        for (int i = 0, iLen = redisMsgList.size(); i < iLen; i++) {
            RedisMsg redisMsg = redisMsgList.get(i);
            String uuid = redisMsg.getUuid();
            String msg = redisMsg.getMsg();
            
            int msgResultIndex = i * 2;
            int pendingResultIndex = msgResultIndex + 1;
            Object msgResult = sendResultSize > msgResultIndex && sendResultList != null ? sendResultList.get(msgResultIndex) : null;
            Object pendingResult = sendResultSize > pendingResultIndex && sendResultList != null ? sendResultList.get(pendingResultIndex) : null;
            
            boolean success = true;
            if (msgResult == null || pendingResult == null) {
                success = false;
                LOGGER.error("send msg:{}, content:{} fail, redis result size != 2", uuid, msg);
            } else {
                if(!((msgResult instanceof Long) && msgResult.equals(1L))) {
                    success = false;
                    LOGGER.error("send msg:{}, content:{} fail, redis result[0] != 1", uuid, msg);
                }
                if(!((pendingResult instanceof Long) && ((Long) pendingResult) > 0L)) {
                    success = false;
                    LOGGER.error("send msg:{}, content:{} fail, redis result[1] < 1", uuid, msg);
                }
            }
            resultList.add(success ? uuid : null);
        }
        return resultList;
    }
    
    /**
     * 接收消息，永久阻塞式，使用默认的actTimeout值
     * @param redisHelper
     * @param topic 即redis的key
     * @return
     */
    public static RedisMsg receive(RedisHelper redisHelper, String topic) {
        return receive(redisHelper, topic, -1, null);
    }

    /**
     * 接收消息
     * @param redisHelper
     * @param topic 即redis的key
     * @param waitTimeoutSec 指定接口阻塞等待时间，0表示不阻塞，-1表示永久等待，大于0为等待的秒数
     * @param ackTimeoutSec ack确认超时的秒数，设置为null则表示不修改，使用发送方设置的默认超时值
     * @return 如果没有接收到消息，返回null
     */
    public static RedisMsg receive(RedisHelper redisHelper, String topic, int waitTimeoutSec, Integer ackTimeoutSec) {

        String listKey = getPendingKey(topic);
        String doingKey = getDoingKey(topic);
        String mapKey = getMapKey(topic);

        RedisMsg redisMsg = redisHelper.execute(jedis -> {
            String uuid = null;
            if(waitTimeoutSec == 0) {
                uuid = jedis.rpoplpush(listKey, doingKey);
            } else {
                uuid = jedis.brpoplpush(listKey, doingKey, waitTimeoutSec < 0 ? 0 : waitTimeoutSec);
            }

            if(uuid == null) { // 没有收到消息，属于正常情况
                return null;
            }

            String msgJson = jedis.hget(mapKey, uuid);
            if(msgJson == null || msgJson.isEmpty()) {
                // 说明消息已经被消费了，清理掉uuid即可
                long removedCount = jedis.lrem(doingKey, 0, uuid);
                if (removedCount > 0) {
                    LOGGER.warn("get uuid:{} msg fail, msg is empty, this msg uuid has been removed.", uuid);
                }
                // 如果doing列表里没有，一般是任务超时后被移回到listKey里了，此时等待下一次消费在删除即可
                // 不需要去pendingKey移除，因为当消息堆积时，删除pengdingKey的制定消息的时间复杂度是O(N)
                return null;
            }

            RedisMsg _redisMsg = JsonRedisObjectConverter.parse(msgJson, RedisMsg.class);
            _redisMsg.setRecvTime(System.currentTimeMillis());
            if(ackTimeoutSec != null) {
                _redisMsg.setAckTimeout(ackTimeoutSec);
            }
            if (_redisMsg.getConsumeCount() == null) {
                _redisMsg.setConsumeCount(1);
            } else {
                _redisMsg.setConsumeCount(_redisMsg.getConsumeCount() + 1);
            }
            JedisVersionCompatible.hset(jedis, mapKey, uuid, JsonRedisObjectConverter.toJson(_redisMsg));

            return _redisMsg;
        });

        return redisMsg;
    }

    /**
     * 确认消费消息成功，移除消息
     * @param redisHelper
     * @param topic 即redis的key
     * @param msgUuid
     * @return
     */
    public static boolean ack(RedisHelper redisHelper, String topic, String msgUuid) {
        // String listKey = getPendingKey(topic);
        String doingKey = getDoingKey(topic);
        String mapKey = getMapKey(topic);

        redisHelper.executePipeline(pipeline -> {
            // pipeline.lrem(listKey, 0, msgUuid); // 去掉移除listKey，属于低概率，当消息堆积时，该命令时间复杂度是O(N)
            pipeline.lrem(doingKey, 0, msgUuid);
            pipeline.hdel(mapKey, msgUuid);
        });

        return true;
    }
    
    /**
     * 确认消费消息失败，复原消息
     *    该方法将消息复原后，可立即被消费
     *    而超时的清理复原消息，被消费的频率会被超时时间控制
     * @param redisHelper
     * @param topic 即redis的key
     * @param msgUuid
     * @return
     */
    public static boolean nack(RedisHelper redisHelper, String topic, String msgUuid) {
        recoverMsg(redisHelper, topic, msgUuid);
        return true;
    }
    
    /**
     * 清理整个topic数据
     * @param redisHelper
     * @param topic 即redis的key
     * @return
     */
    public static boolean removeTopic(RedisHelper redisHelper, String topic) {
        String pendingKey = getPendingKey(topic);
        String doingKey = getDoingKey(topic);
        String mapKey = getMapKey(topic);

        List<String> keys = new ArrayList<>();
        keys.add(mapKey);
        keys.add(pendingKey);
        keys.add(doingKey);
        keys.add(REDIS_MSG_QUEUE_TOPICS_KEY);

        List<String> argv = new ArrayList<>();
        argv.add(topic);

        return redisHelper.execute(jedis -> {
            jedis.eval("redis.call('DEL', KEYS[1]); redis.call('DEL', KEYS[2]); redis.call('DEL', KEYS[3]); redis.call('SREM', KEYS[4], ARGV[1]);",
                    keys, argv
            );
            return true;
        });
    }

    public static RedisQueueStatus getQueueStatus(RedisHelper redisHelper, String topic) {
        String pendingKey = getPendingKey(topic);
        String doingKey = getDoingKey(topic);

        RedisQueueStatus status = new RedisQueueStatus();
        Long pendingLen = redisHelper.execute(jedis -> JedisVersionCompatible.llen(jedis, pendingKey));
        Long doingLen = redisHelper.execute(jedis -> JedisVersionCompatible.llen(jedis, doingKey));

        status.setPendingCount(pendingLen == null ? 0 : pendingLen.intValue());
        status.setDoingCount(doingLen == null ? 0 : doingLen.intValue());

        return status;
    }

    ////////////// 以下是清理任务相关的

    /**获得消息体，如果不存在返回null*/
    private static RedisMsg getMsg(RedisHelper redisHelper, String topic, String uuid) {
        String mapKey = getMapKey(topic);

        String json = redisHelper.execute(jedis -> jedis.hget(mapKey, uuid));
        if(json == null || json.isEmpty()) {
            return null;
        }
        RedisMsg redisMsg = JsonRedisObjectConverter.parse(json, RedisMsg.class);
        return redisMsg;
    }

    /**查询超时的消息，里面包含了消费时间为null的消息，外层清理时需要10秒延迟清理*/
    private static List<RedisMsg> getExpireDoingMsg(RedisHelper redisHelper, String topic) {

        String doingKey = getDoingKey(topic);
        String mapKey = getMapKey(topic);

        List<RedisMsg> expireMsg  = redisHelper.execute(jedis -> {
            List<String> uuidList = jedis.lrange(doingKey, 0, -1);

            List<RedisMsg> _expireMsg = new ArrayList<>();

            for(String uuid : uuidList) {
                String json = jedis.hget(mapKey, uuid);
                if(json == null || json.isEmpty()) {
                    // 清理不存在消息体的doing uuid
                    jedis.lrem(doingKey, 0, uuid);
                    LOGGER.warn("topic:{}, clear not exist DOING msg uuid:{}", topic, uuid);
                    continue;
                }
                RedisMsg redisMsg = JsonRedisObjectConverter.parse(json, RedisMsg.class);
                long now = System.currentTimeMillis();
                if(redisMsg.getRecvTime() == null || redisMsg.getRecvTime() + redisMsg.getAckTimeout() * 1000 < now) {
                    _expireMsg.add(redisMsg);
                }
            }

            return _expireMsg;
        });

        return expireMsg;
    }

    /**
     * 复原消息
     *   1. 超时清理
     *   2. nack
     */
    private static void recoverMsg(RedisHelper redisHelper, String topic, String uuid) {

        String pendingKey = getPendingKey(topic);
        String doingKey = getDoingKey(topic);
        
        // 获取消息信息，如果为null，表示消息不存在
        RedisMsg redisMsg = getMsg(redisHelper, topic, uuid);
        if (redisMsg == null) {
            redisHelper.execute(jedis -> jedis.lrem(doingKey, 0, uuid));
            // 不要移除pendingKey中的uuid，当消息堆积时，移除的时间复杂度是O(N)，堆积消息达到百万级别时，此命令会非常慢
            //redisHelper.execute(jedis -> jedis.eval(
            //        "redis.call('LREM', KEYS[1], 0, ARGV[1]); redis.call('LREM', KEYS[2], 0, ARGV[1]); ",
            //        ListUtils.newArrayList(doingKey, pendingKey), ListUtils.newArrayList(uuid)));
            // 那么pendingKey中的消息会何时被移除呢？它会在被消费时，查到MSG没有消息体了，被删除，此时时间复杂度为O(1)

            return;
        }
    
        // 必须将消息接收时间重置为null
        redisMsg.setRecvTime(null);
        redisHelper.execute(jedis -> {
            // 判断消息是否已经存在，可能已经被ack，不能再设置回去
            // 这里如果是先加后删，则比较大概率被等待receive的客户端拿到之后pop push回doing列表，此动作如果在删除之前进行，就会出现误删情况
            // 如果是先删后加，理论上不会有问题，除了极端情况下，redis执行了第一条命令之后redis挂了（还不是网络原因，而是redis断电）才可能导致丢失数据，但这种可能性已经远远比第一种低
            String mapKey = getMapKey(topic);

            List<String> keys = new ArrayList<>();
            keys.add(mapKey);
            keys.add(doingKey);
            keys.add(pendingKey);

            List<String> argv = new ArrayList<>();
            argv.add(uuid);
            argv.add(JsonRedisObjectConverter.toJson(redisMsg));

            return jedis.eval(
                    "if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 1 then " +
                            "   redis.call('HSET', KEYS[1], ARGV[1], ARGV[2]) " +
                            "   redis.call('LREM', KEYS[2], 0, ARGV[1]) " +
                            "   redis.call('LPUSH', KEYS[3], ARGV[1]) " +
                            " else " +
                            "   redis.call('LREM', KEYS[2], 0, ARGV[1])" +
                            " end",
                    keys, argv);
        });
    }

    /**清理消息队列中，uuid已经不存在了，但是map中还在的消息，每天清理一次即可（这类消息是低概率且只有可能在发送环节出现），要根据发送时间延迟清理*/
    // public static void clearMap(RedisHelper redisHelper, String topic) {
    // 暂不实现该方法
    //}

    // 恢复超时消息，每10秒跑一次
    public static class RecoverMsgTask extends Thread {

        private RedisHelper redisHelper;
        private Map<String, String> topics = new ConcurrentHashMap<>(); // 存放需要更新的主题

        public RecoverMsgTask(RedisHelper redisHelper) {
            this.redisHelper = redisHelper;
        }

        public void addTopic(String topic) {
            if(topics.containsKey(topic)) { // 本地缓存，避免频繁写入redis
                return;
            }
            topics.put(topic, "");
            redisHelper.execute(jedis -> JedisVersionCompatible.sadd(jedis, REDIS_MSG_QUEUE_TOPICS_KEY, topic));
        }

        /**清理过期消息，返回true表示有消费时间为null的情况，已经睡眠了10秒去清理了；返回false则表示没有*/
        private boolean doClean() {
            Map<String, List<String>> waitToClear = new HashMap<>(); // 等待清理的topic -> 消息uuid列表

            Set<String> topicsInRedis = redisHelper.execute(jedis -> jedis.smembers(REDIS_MSG_QUEUE_TOPICS_KEY));
            // 同步一下，如果topicsInRedis中没有但是本地有，则本地删除掉
            for(String t : topics.keySet()) {
                if (!topicsInRedis.contains(t)) {
                    topics.remove(t);
                }
            }

            for(String topic : topicsInRedis) {
                List<RedisMsg> expires = getExpireDoingMsg(redisHelper, topic);
                if(expires.isEmpty()) {
                    continue; // 不需要处理
                }

                LOGGER.warn("expire topic:{} msg count:{}", topic, expires.size());
                List<String> nullRecvTimeList = new ArrayList<>();

                for(RedisMsg redisMsg : expires) {
                    if(redisMsg.getRecvTime() == null) {
                        nullRecvTimeList.add(redisMsg.getUuid());
                    } else {
                        recoverMsg(redisHelper, topic, redisMsg.getUuid());
                    }
                }

                if(!nullRecvTimeList.isEmpty()) {
                    LOGGER.warn("expire topic:{} msg with null recvTime count:{}, msg uuids:{}",
                            topic, nullRecvTimeList.size(), JsonRedisObjectConverter.toJson(nullRecvTimeList));
                    waitToClear.put(topic, nullRecvTimeList);
                }
            }

            if(!waitToClear.isEmpty()) { // 清理，需要再检查一遍是否消费时间确实为null
                try {
                    Thread.sleep(10000); // 这个10秒钟还关乎消费时间为null的消息的延迟处理
                } catch (InterruptedException e) { // ignore
                }

                for(Map.Entry<String, List<String>> entry : waitToClear.entrySet()) {
                    for(String uuid : entry.getValue()) {
                        RedisMsg msg = getMsg(redisHelper, entry.getKey(), uuid);
                        if(msg != null && msg.getRecvTime() == null) {
                            recoverMsg(redisHelper, entry.getKey(), uuid);
                        }
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        private static final String LOCK_KEY = "_RedisMsgQueueRecoverMsgTaskLock_";

        /** 一直尝试拿锁，直到拿到为止 */
        private String getLock() {
            while(true) {
                try {
                    String lockUuid = redisHelper.requireLock(LOCK_KEY, "-", 30, true);
                    if(lockUuid == null) { // 没有拿到锁，等待30秒重试
                        doSleep(30000);
                    } else {
                        return lockUuid;
                    }
                } catch (Throwable e) {
                    LOGGER.error("get lock error, namespace:{} key:-", LOCK_KEY, e);
                    doSleep(30000);
                }
            }
        }

        private void doSleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) { // ignore
            }
        }

        @Override
        public void run() {
            final AtomicReference<String> lockUuidRef = new AtomicReference<>();

            Thread thread = new Thread(() -> {
                while(true) { // 一直循环不会退出，每5秒续30秒，也即一共有5次续期机会
                    if(lockUuidRef.get() != null) {
                        boolean result = redisHelper.renewalLock(LOCK_KEY, "-",
                                lockUuidRef.get(),30);
                        if (!result) { // 如果刷新锁失败，则废弃该锁
                            lockUuidRef.set(null);
                        }
                    }
                    doSleep(5000);
                }
            });
            thread.setName("RedisMsgQueue.RecoverMsgRenewalLockTask");
            thread.start();

            while(true) { // 一直循环不会退出
                String lockUuid = getLock();
                lockUuidRef.set(lockUuid);

                while(true) { // 一直循环不会退出，除非锁不见了
                    try {
                        boolean hasSleep = doClean();
                        if(!hasSleep) { // 如果刚清理完，还没睡眠，则睡眠一下，避免过于频繁清理，消息恢复的延迟级别是10-30秒
                            doSleep(10000);
                        }
                    } catch (Exception e) {
                        LOGGER.error("do clean task fail", e);
                    }
                    if(lockUuidRef.get() == null) { // 锁丢失了就重新拿锁
                        break;
                    }
                }
            }
        }
    }

}
