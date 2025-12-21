package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.sendmsg.SendMsgTestService;
import com.pugwoo.wooutils.redis.RedisHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestSendMsg extends com.pugwoo.redishelpertest.common.TestSendMsg {

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private SendMsgTestService sendMsgTestService;

    @Override
    public RedisHelper getRedisHelper() {
        return redisHelper;
    }

    @Override
    public SendMsgTestService getSendMsgTestService() {
        return sendMsgTestService;
    }
}

