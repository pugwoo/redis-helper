package com.pugwoo.redishelpertest.redis.sync;

import com.pugwoo.wooutils.redis.Synchronized;

import java.util.Date;

public class HelloService {
	
	@Synchronized(namespace = "hello", keyScript = "args[0]")
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
