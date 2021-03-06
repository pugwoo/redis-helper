package com.pugwoo.wooutils.redis;

import com.pugwoo.wooutils.redis.impl.JsonRedisObjectConverter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.mvel2.MVEL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EnableAspectJAutoProxy
@Aspect
public class RedisSyncAspect implements InitializingBean {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RedisSyncAspect.class);

	@Autowired
	private RedisHelper redisHelper;

	private static class HeartBeatInfo {
		public Integer heartbeatExpireSecond;
		public String namespace;
		public String key;
		public String lockUuid;
	}

	private static final Map<String, HeartBeatInfo> heartBeatKeys = new ConcurrentHashMap<>(); // 心跳超时秒数

	private static volatile HeartbeatRenewalTask heartbeatRenewalTask = null; // 不需要多线程

	@Override
	public void afterPropertiesSet() {
		if(redisHelper == null) {
			LOGGER.error("redisHelper is null, RedisSyncAspect will pass through all method call");
		} else {
			heartbeatRenewalTask = new HeartbeatRenewalTask();
			heartbeatRenewalTask.setName("RedisSyncAspect-heartbeat-renewal-thread");
			heartbeatRenewalTask.start();
			LOGGER.info("@Synchronized init success.");
		}
	}

	@Around("@annotation(com.pugwoo.wooutils.redis.Synchronized) execution(* *.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
		if(this.redisHelper == null) {
			LOGGER.error("redisHelper is null, RedisSyncAspect will pass through all method call");
			RedisSyncContext.set(false, true);
			return pjp.proceed();
		}
		
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method targetMethod = signature.getMethod();
		Synchronized sync = targetMethod.getAnnotation(Synchronized.class);
		
		String namespace = sync.namespace();
		int expireSecond = sync.expireSecond();
		int heartbeatExpireSecond = sync.heartbeatExpireSecond();
		int waitLockMillisecond = sync.waitLockMillisecond();

		if(expireSecond <= 0 && heartbeatExpireSecond <= 0) {
			LOGGER.error("one of expireSecond or heartbeatExpireSecond must > 0, now set heartbeatExpireSecond to be 15");
			heartbeatExpireSecond = 15;
		}
		int _expireSecond = expireSecond > 0 ? expireSecond : heartbeatExpireSecond;

		String key = "-";
        String keyScript = sync.keyScript();
        if(!keyScript.trim().isEmpty()) {
			Object[] args = pjp.getArgs();
			Map<String, Object> context = new HashMap<>();
			context.put("args", args);

			try {
				Object result = MVEL.eval(keyScript.trim(), context);
				if(result != null) {
					key = result.toString();
				}
			} catch (Throwable e) {
				LOGGER.error("eval keyScript fail, keyScript:{}, args:{}",
						keyScript, JsonRedisObjectConverter.toJson(args));
			}
		}
		
		int a = 0, b = 1; // 构造兔子数列
		
		long start = System.currentTimeMillis();
		while(true) {
			String lockUuid = redisHelper.requireLock(namespace, key, _expireSecond);
			if(lockUuid != null) {
				if(sync.logDebug()) {
					if(expireSecond > 0) {
						LOGGER.info("namespace:{},key:{},got lock,expireSecond:{},lockUuid:{},threadName:{}",
								namespace, key, expireSecond, lockUuid, Thread.currentThread().getName());
					} else {
						LOGGER.info("namespace:{},key:{},got lock,heartbeatExpireSecond:{},lockUuid:{},threadName:{}",
								namespace, key, heartbeatExpireSecond, lockUuid, Thread.currentThread().getName());
					}
				}

				String uuid = null;
				try {
					if(expireSecond <= 0) { // 此时是心跳机制
						uuid = UUID.randomUUID().toString();
						HeartBeatInfo heartBeatInfo = new HeartBeatInfo();
						heartBeatInfo.namespace = namespace;
						heartBeatInfo.key = key;
						heartBeatInfo.heartbeatExpireSecond = heartbeatExpireSecond;
						heartBeatInfo.lockUuid = lockUuid;
						heartBeatKeys.put(uuid, heartBeatInfo);
					}

					RedisSyncContext.set(true, true);
					return pjp.proceed();
				} finally {
					if(uuid != null) {
						heartBeatKeys.remove(uuid);
					}

					boolean result = redisHelper.releaseLock(namespace, key, lockUuid);
					if(sync.logDebug()) {
						if(result) {
							LOGGER.info("namespace:{},key:{} release lock success, lockUuid:{},threadName:{}",
									namespace, key, lockUuid, Thread.currentThread().getName());
						} else {
							LOGGER.error("namespace:{},key:{} release lock fail, lockUuid:{},threadName:{}",
									namespace, key, lockUuid, Thread.currentThread().getName());
						}
					}
				}
			} else {
				if(sync.logDebug()) {
					LOGGER.info("namespace:{},key:{}, NOT get a lock,threadName:{}", namespace, key,
							Thread.currentThread().getName());
				}
			}
			
			if(waitLockMillisecond == 0) {
				RedisSyncContext.set(true, false);
				if(sync.logDebug()) {
					LOGGER.info("namespace:{},key:{}, give up getting a lock,threadName:{}", namespace, key,
							Thread.currentThread().getName());
				}
				return null;
			}
			long totalWait = System.currentTimeMillis() - start;
			if(totalWait >= waitLockMillisecond) {
				RedisSyncContext.set(true, false);
				if(sync.logDebug()) {
					LOGGER.info("namespace:{},key:{}, give up getting a lock,total wait:{}ms,threadName:{}", namespace, key, totalWait, Thread.currentThread().getName());
				}
				return null;
			}
			if(waitLockMillisecond - totalWait < b) {
				Thread.sleep(waitLockMillisecond - totalWait);
			} else {
				Thread.sleep(b);
				int c = a + b;
				a = b;
				b = c; // 构造兔子数列
				if(b > 1000) {b = 1000;}
			}
		}
    }

	public void setRedisHelper(RedisHelper redisHelper) {
		this.redisHelper = redisHelper;
	}

	private class HeartbeatRenewalTask extends Thread {
		@Override
		public void run() {
			if(redisHelper == null) {
				LOGGER.error("redisHelper is null, HeartbeatRenewalTask stop");
				return;
			}

			while (true) { // 一直循环，不会退出
				for(Map.Entry<String, HeartBeatInfo> key : heartBeatKeys.entrySet()) {
					HeartBeatInfo heartBeatInfo = key.getValue();
					redisHelper.renewalLock(heartBeatInfo.namespace, heartBeatInfo.key,
							heartBeatInfo.lockUuid,
							heartBeatInfo.heartbeatExpireSecond);
				}

				try {
					Thread.sleep(3000); // 3秒heart beat一次
				} catch (InterruptedException e) { // ignore
				}
			}
		}
	}

}
