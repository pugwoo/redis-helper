package com.pugwoo.redishelpertest.ratelimit;

import com.pugwoo.wooutils.redis.RateLimit;
import com.pugwoo.wooutils.redis.RedisLimitPeriodEnum;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    @RateLimit(limitPeriod = RedisLimitPeriodEnum.MINUTE, limitCount = 20000, waitMillisecond = 0)
    public String limitPerMinute(String name) {
        return name;
    }

    // qps峰值大概是800多，所以这里用500才能起到限制效果
    @RateLimit(limitPeriod = RedisLimitPeriodEnum.TEN_SECOND, limitCount = 400, waitMillisecond = 0)
    @RateLimit(limitPeriod = RedisLimitPeriodEnum.MINUTE, limitCount = 1000, waitMillisecond = 0)
    public String limitPerMinute2(String name) {
        return name;
    }

}
