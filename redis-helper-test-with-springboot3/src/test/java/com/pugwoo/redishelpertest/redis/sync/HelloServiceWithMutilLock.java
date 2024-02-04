package com.pugwoo.redishelpertest.redis.sync;

import com.pugwoo.wooutils.redis.Synchronized;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class HelloServiceWithMutilLock {

	int a = 0;

	@Synchronized(namespace = "hello0",waitLockMillisecond = 100000000)
	@Synchronized(namespace = "hello1",waitLockMillisecond = 100000000)
	@Synchronized(namespace = "hello2",waitLockMillisecond = 100000000)
	@Synchronized(namespace = "hello3",waitLockMillisecond = 100000000)
	@Synchronized(namespace = "hello4",waitLockMillisecond = 100000000)
	public void add() throws Exception {
		a++;
	}

	public int getA(){
		return a;
	}

	
	@Synchronized(namespace = "hello", keyScript = "args[0]")
	@Synchronized(namespace = "hello1", keyScript = "args[0]")
	public String hello(String name, int i) throws Exception {

		if(i > 0) {
			System.out.println("hello(" + name + i + ") will sleep 1 seconds" + new Date());
			Thread.sleep(1000);
			System.out.println("sleep done");

			hello(name, -i); // 故意用负值，使得它不用sleep

			return "hello(-)";
		} else {
			// 测试递归场景（只调一次）
			System.out.println("这是递归进来的:name:" + name + ",i:" + i);
			return "";
		}

	}
	
	@Synchronized(keyScript = "args[0]")
	public String helloByDefaultNamespace(String name, int i) throws Exception {
		
		if(i > 0) {
			System.out.println("hello(" + name + i + ") will sleep 1 seconds" + new Date());
			Thread.sleep(1000);
			System.out.println("sleep done");
			
			hello(name, -i); // 故意用负值，使得它不用sleep
			
			return "helloNotSupportNamespace(-)";
		} else {
			// 测试递归场景（只调一次）
			System.out.println("这是递归进来的:name:" + name + ",i:" + i);
			return "";
		}
		
	}
}
