package com.pugwoo.redishelpertest;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.string.StringTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest
public class LockTest extends com.pugwoo.redishelpertest.common.LockTest {

	@Autowired
	private RedisHelper redisHelper;

	@Override
	public RedisHelper getRedisHelper() {
		return redisHelper;
	}

}
