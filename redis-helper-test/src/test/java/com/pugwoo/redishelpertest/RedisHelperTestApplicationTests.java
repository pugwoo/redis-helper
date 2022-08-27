package com.pugwoo.redishelpertest;

import com.pugwoo.wooutils.redis.RedisHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RedisHelperTestApplicationTests {

	@Autowired
	private RedisHelper redisHelper;

	@Test
	void contextLoads() {
		System.out.println(redisHelper);
	}

}
