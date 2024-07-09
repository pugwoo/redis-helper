package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.redis.sync.HeartbeatTestService;
import com.pugwoo.redishelpertest.redis.sync.HelloService;
import com.pugwoo.redishelpertest.redis.sync.ThrowIfNotGetLockTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestSync extends com.pugwoo.redishelpertest.common.TestSync {

	@Autowired
	private HelloService helloService;
	@Autowired
	private HeartbeatTestService heartbeatTestService;
	@Autowired
	private ThrowIfNotGetLockTestService throwIfNotGetLockTestService;

	@Override
	public HelloService getHelloService() {
		return helloService;
	}

	@Override
	public HeartbeatTestService getHeartbeatTestService() {
		return heartbeatTestService;
	}

	@Override
	public ThrowIfNotGetLockTestService getThrowIfNotGetLockTestService() {
		return throwIfNotGetLockTestService;
	}
}
