package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.receivemsg.ReceiveMsgTestService;
import com.pugwoo.wooutils.redis.RedisHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestReceiveMsg extends com.pugwoo.redishelpertest.common.TestReceiveMsg {

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private ReceiveMsgTestService receiveMsgTestService;

    @Override
    public RedisHelper getRedisHelper() {
        return redisHelper;
    }

    @Override
    public ReceiveMsgTestService getReceiveMsgTestService() {
        return receiveMsgTestService;
    }
}

