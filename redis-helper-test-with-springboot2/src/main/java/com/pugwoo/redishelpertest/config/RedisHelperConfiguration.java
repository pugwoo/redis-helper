package com.pugwoo.redishelpertest.config;

import com.pugwoo.redishelpertest.cache.WithCacheDemoService;
import com.pugwoo.redishelpertest.ratelimit.RateLimitService;
import com.pugwoo.redishelpertest.redis.sync.HeartbeatTestService;
import com.pugwoo.redishelpertest.redis.sync.HelloService;
import com.pugwoo.redishelpertest.redis.sync.HelloServiceWithMutilLock;
import com.pugwoo.redishelpertest.redis.sync.ThrowIfNotGetLockTestService;
import com.pugwoo.wooutils.cache.HiSpeedCacheAspect;
import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.RedisLimitAspect;
import com.pugwoo.wooutils.redis.RedisSyncAspect;
import com.pugwoo.wooutils.redis.impl.RedisHelperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisHelperConfiguration {

    @Autowired
    private RedisProperties redisProperties;

    @Bean("redisHelper")
    public RedisHelper redisHelper() {
        RedisHelperImpl redisHelper = new RedisHelperImpl();
        redisHelper.setHost(redisProperties.getHost());
        redisHelper.setPort(redisProperties.getPort());
        redisHelper.setPassword(redisProperties.getPassword());
        redisHelper.setDatabase(redisProperties.getDatabase());

        return redisHelper;
    }

    @Bean
    public RedisSyncAspect redisSyncAspect() {
        return new RedisSyncAspect();
    }

    @Bean
    public RedisLimitAspect redisLimitAspect() {
        return new RedisLimitAspect();
    }

    @Bean
    public HiSpeedCacheAspect hiSpeedCacheAspect() {
        return new HiSpeedCacheAspect();
    }

    @Bean
    public WithCacheDemoService withCacheDemoService() {
        return new WithCacheDemoService();
    }

    @Bean
    public RateLimitService rateLimitService() {
        return new RateLimitService();
    }

    @Bean
    public HeartbeatTestService heartbeatTestService() {
        return new HeartbeatTestService();
    }

    @Bean
    public HelloService helloService() {
        return new HelloService();
    }

    @Bean
    public HelloServiceWithMutilLock helloServiceWithMutilLock() {
        return new HelloServiceWithMutilLock();
    }

    @Bean
    public ThrowIfNotGetLockTestService throwIfNotGetLockTestService() {
        return new ThrowIfNotGetLockTestService();
    }

}
