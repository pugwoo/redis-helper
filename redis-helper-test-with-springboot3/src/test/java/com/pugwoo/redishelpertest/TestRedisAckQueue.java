package com.pugwoo.redishelpertest;

import com.pugwoo.wooutils.redis.RedisHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestRedisAckQueue extends com.pugwoo.redishelpertest.common.TestRedisAckQueue {

    @Autowired
    private RedisHelper redisHelper;

    @Override
    public RedisHelper getRedisHelper() {
        return redisHelper;
    }
}
