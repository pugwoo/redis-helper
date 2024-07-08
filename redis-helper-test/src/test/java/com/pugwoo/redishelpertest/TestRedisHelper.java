package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.redis.Student;
import com.pugwoo.wooutils.lang.EqualUtils;
import com.pugwoo.wooutils.redis.RedisHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.*;

@SpringBootTest
class TestRedisHelper extends com.pugwoo.redishelpertest.common.TestRedisHelper {

	@Autowired
	private RedisHelper redisHelper;

	@Override
	public RedisHelper getRedisHelper() {
		return redisHelper;
	}
}
