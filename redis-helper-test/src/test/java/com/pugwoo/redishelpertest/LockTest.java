package com.pugwoo.redishelpertest;

import com.pugwoo.wooutils.redis.RedisHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@SpringBootTest
public class LockTest {

	@Autowired
	private RedisHelper redisHelper;

	@Test
	public void test() throws Exception {
		final String nameSpace = "myname";
		final String key = "key";

		final int THREAD = 10;
		final int SLEEP = 3000;

		long start = System.currentTimeMillis();

		Set<String> gotLockThreads = new ConcurrentSkipListSet<>();
		List<Thread> threads = new ArrayList<Thread>();

		for(int i=0;i<THREAD;i++){
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");

					// 同一时刻只有一个人可以拿到lock，返回true
					String lockUuid = redisHelper.requireLock(nameSpace, key, 10);
					if(lockUuid != null){
						System.out.println(df.format(new Date()) + Thread.currentThread().getName() + "拿到锁");
					}else{
						System.out.println(df.format(new Date()) + Thread.currentThread().getName() + "没有拿到锁，等待....");
					}
					if(lockUuid == null){
						while (lockUuid == null){
							lockUuid = redisHelper.requireLock(nameSpace, key, 10);
						}
						System.out.println((df.format(new Date()) +
								Thread.currentThread().getName() + "等待后拿到锁"+System.currentTimeMillis()));
					}

					gotLockThreads.add(Thread.currentThread().getName());

					try {
						Thread.sleep(SLEEP);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					boolean succ = redisHelper.releaseLock(nameSpace,key,lockUuid);
					assert succ;
					System.out.println(df.format(new Date()) + Thread.currentThread().getName() + "释放锁,成功:" + succ);
				}
			});
			thread.start();
			threads.add(thread);
		}

		// 主线程等待结束
		for(Thread thread : threads) {
			thread.join();
		}

		long end = System.currentTimeMillis();

		System.out.println("main end, total cost:" + (end - start) + "ms");

		assert gotLockThreads.size() == THREAD;
		assert (end - start) >= THREAD * SLEEP;
		assert (end - start) <= THREAD * SLEEP + 3000; // 预留3秒的耗时
	}

	
}
