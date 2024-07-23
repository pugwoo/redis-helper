package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.redis.sync.HelloServiceWithMutilLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestSyncWithMutilLock extends com.pugwoo.redishelpertest.common.TestSyncWithMutilLock {

    @Autowired
    private HelloServiceWithMutilLock helloServiceWithMutilLock;

    @Override
    public HelloServiceWithMutilLock getHelloServiceWithMutilLock() {
        return helloServiceWithMutilLock;
    }
}
