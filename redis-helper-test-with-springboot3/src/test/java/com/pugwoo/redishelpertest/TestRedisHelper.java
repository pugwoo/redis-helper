package com.pugwoo.redishelpertest;

import com.pugwoo.wooutils.redis.RedisHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TestRedisHelper extends com.pugwoo.redishelpertest.common.TestRedisHelper {

	@Autowired
	private RedisHelper redisHelper;

	@Override
	public RedisHelper getRedisHelper() {
		return redisHelper;
	}
}
