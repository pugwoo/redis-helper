package com.pugwoo.redishelperbenchmark.config;

import com.pugwoo.wooutils.cache.HiSpeedCacheAspect;
import com.pugwoo.wooutils.redis.RedisHelper;
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

        redisHelper.setMaxConnection(1000); // 压测时提高连接数

        return redisHelper;
    }

    @Bean
    public RedisSyncAspect redisSyncAspect() {
        return new RedisSyncAspect();
    }

    @Bean
    public HiSpeedCacheAspect hiSpeedCacheAspect() {
        return new HiSpeedCacheAspect();
    }

}
