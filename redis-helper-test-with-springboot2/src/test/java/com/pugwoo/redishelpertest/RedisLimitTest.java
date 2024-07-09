package com.pugwoo.redishelpertest;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.RedisLimitParam;
import com.pugwoo.wooutils.redis.RedisLimitPeriodEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

@SpringBootTest
public class RedisLimitTest extends com.pugwoo.redishelpertest.common.RedisLimitTest {

	@Autowired
	private RedisHelper redisHelper;

	@Override
	public RedisHelper getRedisHelper() {
		return redisHelper;
	}
}
