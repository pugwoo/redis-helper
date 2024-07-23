package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.redis.sync.HelloServiceWithMutilLock;
import com.pugwoo.wooutils.redis.RedisSyncContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class TestSyncWithMutilLock extends com.pugwoo.redishelpertest.common.TestSyncWithMutilLock {

    @Autowired
    private HelloServiceWithMutilLock helloServiceWithMutilLock;

    @Override
    public HelloServiceWithMutilLock getHelloServiceWithMutilLock() {
        return helloServiceWithMutilLock;
    }
}
