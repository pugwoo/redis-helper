package com.pugwoo.wooutils;

import com.pugwoo.wooutils.redis.RedisSyncContext;
import com.pugwoo.wooutils.redis.sync.HeartbeatTestService;
import com.pugwoo.wooutils.redis.sync.HelloService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

@ContextConfiguration(locations = {"classpath:applicationContext-context.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class TestSync {

	@Autowired
	private HelloService helloService;
	@Autowired
	private HeartbeatTestService heartbeatTestService;

	@Test
	public void testHeartbeat() throws Exception {

		List<Thread> thread = new ArrayList<>();

		long start = System.currentTimeMillis();
		// 起3个线程跑，因为有分布式锁，每个跑15秒，45秒
		for(int i = 0; i < 3; i++) {
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					heartbeatTestService.longTask();
				}
			});
			t.start();
			thread.add(t);
		}

		for(Thread t : thread) {
			t.join();
		}

		long end = System.currentTimeMillis();
		assert end - start >= 45000;
		assert end - start <= 50000;
	}

	@Test
	public void testHello() throws Exception {
		List<Thread> thread = new ArrayList<>();

		long start = System.currentTimeMillis();

		for(int i = 1; i <= 10; i++) {
			final int a = i;
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						while (true) {
							helloService.hello("nick", a);
							System.out.println("线程" + a +
									"执行结果详情: 是否执行了方法:" + RedisSyncContext.getHaveRun());
							if(RedisSyncContext.getHaveRun()) {
								break;
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			t.start();
			thread.add(t);
		}

		for(Thread t : thread) {
			t.join();
		}

		long end = System.currentTimeMillis();
		assert end - start >= 10000;
		assert end - start <= 19000;

	}
	
}
