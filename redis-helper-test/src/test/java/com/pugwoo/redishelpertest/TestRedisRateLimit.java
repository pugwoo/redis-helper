package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.ratelimit.RateLimitService;
import com.pugwoo.wooutils.collect.ListUtils;
import com.pugwoo.wooutils.collect.MapUtils;
import com.pugwoo.wooutils.json.JSON;
import com.pugwoo.wooutils.lang.DateUtils;
import com.pugwoo.wooutils.redis.exception.ExceedRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest
public class TestRedisRateLimit extends com.pugwoo.redishelpertest.common.TestRedisRateLimit {

    @Autowired
    private RateLimitService rateLimitService;

    @Override
    public RateLimitService getRateLimitService() {
        return rateLimitService;
    }
}
