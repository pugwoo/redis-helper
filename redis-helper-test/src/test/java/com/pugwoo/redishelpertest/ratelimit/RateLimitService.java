package com.pugwoo.redishelpertest.ratelimit;

import com.pugwoo.wooutils.redis.RateLimit;
import com.pugwoo.wooutils.redis.RedisLimitPeroidEnum;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    @RateLimit(limitPeriod = RedisLimitPeroidEnum.MINUTE, limitCount = 20000, waitMillisecond = 0)
    public String limitPerMinute(String name) {
        return name;
    }

}
