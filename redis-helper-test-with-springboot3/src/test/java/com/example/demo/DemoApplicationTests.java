package com.example.demo;

import com.pugwoo.wooutils.redis.RedisHelper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoApplicationTests {

	@Resource
	private RedisHelper redisHelper;

	@Test
	void contextLoads() {
		System.out.println(redisHelper);
		redisHelper.setString("aaaa", 60, "bbbb");
		System.out.println(redisHelper.getString("aaaa"));
	}

}
