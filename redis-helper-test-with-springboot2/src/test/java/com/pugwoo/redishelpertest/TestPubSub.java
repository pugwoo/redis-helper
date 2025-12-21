package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.pubsub.PubSubTestService;
import com.pugwoo.redishelpertest.pubsub.SubscribeTestService;
import com.pugwoo.wooutils.redis.RedisHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestPubSub extends com.pugwoo.redishelpertest.common.TestPubSub {

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private PubSubTestService pubSubTestService;

    @Autowired
    private SubscribeTestService subscribeTestService;

    @Override
    public RedisHelper getRedisHelper() {
        return redisHelper;
    }

    @Override
    public PubSubTestService getPubSubTestService() {
        return pubSubTestService;
    }

    @Override
    public SubscribeTestService getSubscribeTestService() {
        return subscribeTestService;
    }
}

