package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.cache.WithCacheDemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestHiSpeedCache_default extends TestHiSpeedCacheAbstract {

    @Autowired
    private WithCacheDemoService withCacheDemoService;

    @Override
    public WithCacheDemoService getWithCacheDemoService() {
        return withCacheDemoService;
    }

}
