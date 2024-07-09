package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.ratelimit.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestRedisRateLimit extends com.pugwoo.redishelpertest.common.TestRedisRateLimit {

    @Autowired
    private RateLimitService rateLimitService;

    @Override
    public RateLimitService getRateLimitService() {
        return rateLimitService;
    }
}
