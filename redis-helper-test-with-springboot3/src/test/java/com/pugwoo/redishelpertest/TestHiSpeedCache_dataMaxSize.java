package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.cache.WithCacheDemoService;
import com.pugwoo.redishelpertest.common.TestHiSpeedCacheAbstract;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(locations = {"classpath:applicationContext-context-HiSpeedCache-dataMaxSize.xml"})
@ExtendWith(SpringExtension.class)
public class TestHiSpeedCache_dataMaxSize extends TestHiSpeedCacheAbstract {

    @Autowired
    private WithCacheDemoService withCacheDemoService;

    @Override
    public WithCacheDemoService getWithCacheDemoService() {
        return withCacheDemoService;
    }

}
