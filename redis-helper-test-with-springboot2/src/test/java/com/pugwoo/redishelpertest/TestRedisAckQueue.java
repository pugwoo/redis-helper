package com.pugwoo.redishelpertest;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.RedisMsg;
import com.pugwoo.wooutils.redis.RedisQueueStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
public class TestRedisAckQueue extends com.pugwoo.redishelpertest.common.TestRedisAckQueue {

    @Autowired
    private RedisHelper redisHelper;

    @Override
    public RedisHelper getRedisHelper() {
        return redisHelper;
    }
}
