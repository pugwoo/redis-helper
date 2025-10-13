package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.redis.sync.LockModeTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestLockMode extends com.pugwoo.redishelpertest.common.TestLockMode {

    @Autowired
    private LockModeTestService lockModeTestService;

    @Override
    public LockModeTestService getLockModeTestService() {
        return lockModeTestService;
    }
}
