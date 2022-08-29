package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.redis.sync.HeartbeatTestService;
import com.pugwoo.redishelpertest.redis.sync.HelloService;
import com.pugwoo.redishelpertest.redis.sync.ThrowIfNotGetLockTestService;
import com.pugwoo.wooutils.redis.NotGetLockException;
import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.RedisSyncContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class TestSync {
	@Autowired
	private RedisHelper redisHelper;
	@Autowired
	private HelloService helloService;
	@Autowired
	private HeartbeatTestService heartbeatTestService;
	@Autowired
	private ThrowIfNotGetLockTestService throwIfNotGetLockTestService;

	public static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss.SSS");
	
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
	
	@Test
	public void testHelloByDefaultNamespace() throws Exception {
		List<Thread> thread = new ArrayList<>();
		
		long start = System.currentTimeMillis();
		
		for(int i = 1; i <= 10; i++) {
			final int a = i;
			Thread t = new Thread(() -> {
				try {
					do {
						helloService.helloByDefaultNamespace("nick", a);
						System.out.println("线程" + a +
								"执行结果详情: 是否执行了方法:" + RedisSyncContext.getHaveRun());
					} while (!RedisSyncContext.getHaveRun());
				} catch (Exception e) {
					e.printStackTrace();
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
	
	@Test
	public void testNotThrowIfNotGetLock() throws InterruptedException {
		// 先跑一下，其初次执行大约占了两秒时间，导致后面校验时的时间不对
		// redis操作加锁的时候会生成随机的value
		UUID ignore = UUID.randomUUID();

		List<Thread> thread = new ArrayList<>();
		AtomicInteger haveRunCount = new AtomicInteger();
		AtomicInteger haveNotRunCount = new AtomicInteger();
		long start = System.currentTimeMillis();
		for(int i = 1; i <= 3; i++) {
			final int a = i;
			Thread t = new Thread(() -> {
				try {
					while (true) {
						throwIfNotGetLockTestService.notThrowIfNotGetLock(a, 1000);
						System.out.println(DTF.format(LocalDateTime.now()) + " 线程" + a +
								"执行结果详情: 是否执行了方法:" + RedisSyncContext.getHaveRun());
						if(RedisSyncContext.getHaveRun()) {
							haveRunCount.incrementAndGet();
							break;
						} else {
							haveNotRunCount.incrementAndGet();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
			t.start();
			thread.add(t);
		}
		for(Thread t : thread) { t.join(); }
		long end = System.currentTimeMillis();
		long cost = end - start;
		
		System.out.println("haveRunCount: " + haveRunCount);
		System.out.println("haveNotRunCount: " + haveNotRunCount);
		System.out.println(cost);
		
		assert haveRunCount.get() == 3;    // 总共执行三次
		assert haveNotRunCount.get() == 3;  // 第一个执行时，两个失败了，第二次执行时，一个失败了，加起来三次
		assert cost >= 3000;
		assert cost <= 4000;
	}
	
	@Test
	public void testThrowIfNotGetLock() throws InterruptedException {
		// 先跑一下，其初次执行大约占了两秒时间，导致后面校验时的时间不对
		// redis操作加锁的时候会生成随机的value
		UUID ignore = UUID.randomUUID();

		List<Thread> thread = new ArrayList<>();
		AtomicInteger haveRunCount = new AtomicInteger();
		AtomicInteger haveNotRunCount = new AtomicInteger();
		AtomicInteger haveNotRunExceptionCount = new AtomicInteger();
		AtomicInteger haveNotRunNotThrowIfNotGetLockExceptionCount = new AtomicInteger();
		long start = System.currentTimeMillis();
		for(int i = 1; i <= 3; i++) {
			final int a = i;
			Thread t = new Thread(() -> {
				try {
					while (true) {
						throwIfNotGetLockTestService.throwIfNotGetLock(1000L);
						System.out.println(DTF.format(LocalDateTime.now()) + " 线程" + a +
								"执行结果详情: 是否执行了方法:" + RedisSyncContext.getHaveRun());
						if(RedisSyncContext.getHaveRun()) {
							haveRunCount.incrementAndGet();
							break;
						} else {
							haveNotRunCount.incrementAndGet();
						}
					}
				} catch (Exception e) {
					System.out.println("线程" + a + "抛出了异常; " +
							"执行结果详情: 是否执行了方法:" + RedisSyncContext.getHaveRun());
					haveNotRunExceptionCount.incrementAndGet();
					if (e.getClass() == NotGetLockException.class) {
						NotGetLockException notGetLockException = (NotGetLockException) e;
						System.out.println("targetMethod -> " + notGetLockException.getTargetMethod());
						System.out.println("   namespace -> " + notGetLockException.getNamespace());
						System.out.println("         key -> " + notGetLockException.getKey());
						haveNotRunNotThrowIfNotGetLockExceptionCount.incrementAndGet();
					}
					e.printStackTrace();
				}
			});
			t.start();
			thread.add(t);
		}
		
		for(Thread t : thread) { t.join(); }
		long end = System.currentTimeMillis();
		long cost = end - start;
		
		System.out.println("haveRunCount: " + haveRunCount);
		System.out.println("haveNotRunCount: " + haveNotRunCount);
		System.out.println("haveNotRunExceptionCount: " + haveNotRunExceptionCount);
		System.out.println("haveNotRunNotThrowIfNotGetLockExceptionCount: " + haveNotRunNotThrowIfNotGetLockExceptionCount);
		System.out.println("cost: " + cost);
		
		assert haveRunCount.get() == 1;    // 总共执行1
		assert haveNotRunCount.get() == 0;  // 其他都失败了 异常了
		assert haveNotRunExceptionCount.get() == 2; // 2个失败了 抛异常了
		assert haveNotRunNotThrowIfNotGetLockExceptionCount.get() ==  2;  // 都抛出了NotGetLockException
		assert cost >= 1000;
		assert cost <= 2000;
	}
}
