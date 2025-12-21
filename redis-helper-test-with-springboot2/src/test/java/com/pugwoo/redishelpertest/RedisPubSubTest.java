package com.pugwoo.redishelpertest;

import com.pugwoo.wooutils.redis.RedisHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RedisPubSubTest extends com.pugwoo.redishelpertest.common.RedisPubSubTest {

    @Autowired
    private RedisHelper redisHelper;

    @Override
    public RedisHelper getRedisHelper() {
        return redisHelper;
    }
}

