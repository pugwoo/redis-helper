package com.pugwoo.wooutils.redis;

import com.pugwoo.wooutils.redis.impl.JsonRedisObjectConverter;
import com.pugwoo.wooutils.utils.ClassUtils;
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
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EnableAspectJAutoProxy
@Aspect
@Order(2000)
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

    private final long startTimestamp = System.currentTimeMillis();

    @Override
    public void afterPropertiesSet() {
        long cost = System.currentTimeMillis() - startTimestamp;
        if (redisHelper == null) {
            LOGGER.error("redisHelper is null, RedisSyncAspect will pass through all method call");
        } else {
            heartbeatRenewalTask = new HeartbeatRenewalTask();
            heartbeatRenewalTask.setName("RedisSyncAspect-heartbeat-renewal-thread");
            heartbeatRenewalTask.start();
            LOGGER.info("@Synchronized init success, cost:{} ms.", cost);
        }
    }

    /**
     * 这里是多个加锁的实现
     */
    @Around("@annotation(com.pugwoo.wooutils.redis.Synchronizeds) execution(* *.*(..))")
    public Object arounds(ProceedingJoinPoint pjp) throws Throwable {
        RedisSyncContext.set(false, false); // init初始化
        return processAround(pjp, true);
    }

    @Around("@annotation(com.pugwoo.wooutils.redis.Synchronized) execution(* *.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RedisSyncContext.set(false, false); // init初始化
        return processAround(pjp, false);
    }

    private Object processAround(ProceedingJoinPoint pjp, boolean isMultiLock) throws Throwable {
        // if not set redis, process method
        if (this.redisHelper == null) {
            LOGGER.error("redisHelper is null, RedisSyncAspect will pass through all method call");
            RedisSyncContext.set(false, true);
            return pjp.proceed();
        }
        List<RedisSyncParam> params;
        if (!isMultiLock) {
            params = toList(constructParam(pjp));
        } else {
            params = constructParams(pjp);
        }

        return proceed(params, 0, System.currentTimeMillis());
    }

    private Object proceed(List<RedisSyncParam> params, int i, long startTime) throws Throwable {
        RedisSyncParam param = params.get(i);
        RedisSyncRet redisSyncRet = tryGetLockWithRabbitSeries(param);

        // not get redis lock
        if (!redisSyncRet.successGetLock) {
            return null;
        }

        // if all redis lock got, then process method, otherwise get next redis lock
        try {
            if (params.size() - 1 == i) {
                RedisSyncContext.recordSuccessTotalLockCost(System.currentTimeMillis() - startTime);
                return param.pjp.proceed();
            }
            return proceed(params, i + 1, startTime);
        } finally {
            releaseLock(param, redisSyncRet);
        }
    }

    private List<RedisSyncParam> constructParams(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method targetMethod = signature.getMethod();
        Synchronizeds syncs = targetMethod.getAnnotation(Synchronizeds.class);
        Synchronized[] value = syncs.value();

        List<RedisSyncParam> params = new ArrayList<>();
        for (Synchronized sync : value) {
            RedisSyncParam param = constructParam(sync, targetMethod, pjp.getArgs());
            param.sync = sync;
            param.targetMethod = targetMethod;
            param.pjp = pjp;
            params.add(param);
        }
        return params;
    }


    private List<RedisSyncParam> toList(RedisSyncParam p) {
        List<RedisSyncParam> ret = new ArrayList<>();
        ret.add(p);
        return ret;
    }

    private void releaseLock(RedisSyncParam p, RedisSyncRet redisSyncRet) {
        /*
         * 假如这里remove 了， 另一个线程此时已经进入了 renewalLock
         *  这里先 releaseLock, 然后  renewalLock 生效了, 造成多一次续约
         */
        if (redisSyncRet.uuid != null) {
            heartBeatKeys.remove(redisSyncRet.uuid);
        }
        boolean result = redisHelper.releaseLock(p.namespace, p.key, redisSyncRet.lockUuid, p.isReentrantLock);
        logReleaseLock(p, redisSyncRet.lockUuid, result);
    }

    private RedisSyncRet tryGetLockWithRabbitSeries(RedisSyncParam p) throws Throwable {

        // 构造兔子数列
        int a = 0, b = 1;
        long start = System.currentTimeMillis();
        int tmpExpireSecond = p.expireSecond > 0 ? p.expireSecond : p.heartbeatExpireSecond;
        for (int i = 1; true; i++) {

            long lockStart = System.currentTimeMillis();
            String lockUuid = redisHelper.requireLock(p.namespace, p.key, tmpExpireSecond, p.isReentrantLock);

            if (lockUuid != null) {

                // 获取到了锁，到这里之前发生了 GC或者获取锁的时候网络TTL大，导致锁过期了，则进行解锁并等待下一轮获取
                if (System.currentTimeMillis() - lockStart > tmpExpireSecond * 1000L) {
                    boolean result = redisHelper.releaseLock(p.namespace, p.key, lockUuid, p.isReentrantLock);
                    logReleaseLock(p, lockUuid, result);
                    return RedisSyncRet.notSuccessGetLock(System.currentTimeMillis() - start, i);
                }

                logSuccessGetLock(p, lockUuid);

                String uuid = null;
                if (p.expireSecond <= 0) { // 此时是心跳机制
                    uuid = putToHeatBeat(p, lockUuid);
                }

                RedisSyncContext.set(true, true);
                return RedisSyncRet.successGetLock(uuid, lockUuid, System.currentTimeMillis() - start, i);
            }

            if (p.logDebug) {
                LOGGER.info("namespace:{},key:{}, NOT get a lock,threadName:{}", p.namespace, p.key,
                        Thread.currentThread().getName());
            }

            if (p.waitLockMillisecond == 0) {
                RedisSyncContext.set(true, false);
                if (p.logDebug) {
                    LOGGER.info("namespace:{},key:{}, give up getting a lock,threadName:{}", p.namespace, p.key,
                            Thread.currentThread().getName());
                }
                mayThrowExceptionIfNotGetLock(p.sync, p.targetMethod, p.namespace, p.key);
                return RedisSyncRet.notSuccessGetLock(System.currentTimeMillis() - start, i);
            }

            long totalWait = System.currentTimeMillis() - start;
            if (totalWait >= p.waitLockMillisecond) {
                RedisSyncContext.set(true, false);
                if (p.logDebug) {
                    LOGGER.info("namespace:{},key:{}, give up getting a lock,total wait:{}ms,threadName:{}",
                            p.namespace, p.key, totalWait, Thread.currentThread().getName());
                }
                mayThrowExceptionIfNotGetLock(p.sync, p.targetMethod, p.namespace, p.key);
                return RedisSyncRet.notSuccessGetLock(System.currentTimeMillis() - start, i);
            }
            if (p.waitLockMillisecond - totalWait < b) {
                Thread.sleep(p.waitLockMillisecond - totalWait);
            } else {
                Thread.sleep(b);
                int c = a + b; // 构造兔子数列
                a = b;
                b = c;
                if (b > 1000) {
                    b = 1000;
                }
            }
        }
    }

    private void logReleaseLock(RedisSyncParam p, String lockUuid, boolean result) {
        if (p.logDebug) {
            if (result) {
                LOGGER.info("namespace:{},key:{} release lock success, lockUuid:{},threadName:{}",
                        p.namespace, p.key, lockUuid, Thread.currentThread().getName());
            } else {
                LOGGER.error("namespace:{},key:{} release lock fail, lockUuid:{},threadName:{}",
                        p.namespace, p.key, lockUuid, Thread.currentThread().getName());
            }
        }
    }

    private void logSuccessGetLock(RedisSyncParam p, String lockUuid) {
        if (p.logDebug) {
            if (p.expireSecond > 0) {
                LOGGER.info("namespace:{},key:{},got lock,expireSecond:{},lockUuid:{},threadName:{}",
                        p.namespace, p.key, p.expireSecond, lockUuid, Thread.currentThread().getName());
            } else {
                LOGGER.info("namespace:{},key:{},got lock,heartbeatExpireSecond:{},lockUuid:{},threadName:{}",
                        p.namespace, p.key, p.heartbeatExpireSecond, lockUuid,
                        Thread.currentThread().getName());
            }
        }
    }

    private String putToHeatBeat(RedisSyncParam p, String lockUuid) {
        String uuid = UUID.randomUUID().toString();
        HeartBeatInfo heartBeatInfo = new HeartBeatInfo();
        heartBeatInfo.namespace = p.namespace;
        heartBeatInfo.key = p.key;
        heartBeatInfo.heartbeatExpireSecond = p.heartbeatExpireSecond;
        heartBeatInfo.lockUuid = lockUuid;
        heartBeatKeys.put(uuid, heartBeatInfo);
        return uuid;
    }

    private RedisSyncParam constructParam(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method targetMethod = signature.getMethod();
        Synchronized sync = targetMethod.getAnnotation(Synchronized.class);

        RedisSyncParam param = constructParam(sync, targetMethod, pjp.getArgs());
        param.sync = sync;
        param.targetMethod = targetMethod;
        param.pjp = pjp;
        return param;
    }

    private RedisSyncParam constructParam(Synchronized sync, Method targetMethod, Object[] args) {

        RedisSyncParam param = copyFrom(sync);

        String namespace = sync.namespace();
        if (namespace.trim().isEmpty()) {
            namespace = generateNamespace(targetMethod);
        }
        param.namespace = namespace;

        int expireSecond = sync.expireSecond();
        int heartbeatExpireSecond = sync.heartbeatExpireSecond();

        if (expireSecond <= 0 && heartbeatExpireSecond <= 0) {
            LOGGER.error(
                    "one of expireSecond or heartbeatExpireSecond must > 0, now set heartbeatExpireSecond to be 15");
            heartbeatExpireSecond = 15;
            param.heartbeatExpireSecond = heartbeatExpireSecond;
        }

        String key = "-";
        String keyScript = sync.keyScript();
        if (!keyScript.trim().isEmpty()) {
            Map<String, Object> context = new HashMap<>();
            context.put("args", args);
            try {
                Object result = MVEL.eval(keyScript.trim(), context);
                if (result != null) {
                    key = result.toString();
                }
            } catch (Throwable e) {
                LOGGER.error("eval keyScript fail, keyScript:{}, args:{}",
                        keyScript, JsonRedisObjectConverter.toJson(args));
            }
        }
        param.key = key;

        return param;
    }

    private RedisSyncParam copyFrom(Synchronized sync) {
        RedisSyncParam redisSyncParam = new RedisSyncParam();
        redisSyncParam.namespace = sync.namespace();
        redisSyncParam.keyScript = sync.keyScript();
        redisSyncParam.expireSecond = sync.expireSecond();
        redisSyncParam.heartbeatExpireSecond = sync.heartbeatExpireSecond();
        redisSyncParam.waitLockMillisecond = sync.waitLockMillisecond();
        redisSyncParam.logDebug = sync.logDebug();
        redisSyncParam.throwExceptionIfNotGetLock = sync.throwExceptionIfNotGetLock();
        redisSyncParam.isReentrantLock = sync.isReentrantLock();
        return redisSyncParam;
    }

    /**
     * 如果sync.throwExceptionIfNotGetLock()为true,
     * 则抛出指定的 {@link NotGetLockException} <br>
     *
     * @param sync 注解的实例
     * @param targetMethod 要执行的方法
     * @param namespace 锁的命名空间，异常信息之一
     * @param key 锁的名称，异常信息之一
     */
    private void mayThrowExceptionIfNotGetLock(Synchronized sync, Method targetMethod, String namespace, String key) {
        if (sync.throwExceptionIfNotGetLock()) {
            throw new NotGetLockException(targetMethod, namespace, key);
        }
    }

    public void setRedisHelper(RedisHelper redisHelper) {
        this.redisHelper = redisHelper;
    }

    private class HeartbeatRenewalTask extends Thread {

        @Override
        public void run() {
            if (redisHelper == null) {
                LOGGER.error("redisHelper is null, HeartbeatRenewalTask stop");
                return;
            }

            while (true) { // 一直循环，不会退出
                long start = System.currentTimeMillis();
                for (String key : heartBeatKeys.keySet()) {
                    HeartBeatInfo heartBeatInfo = heartBeatKeys.get(key);
                    // 相当于double-check
                    if (heartBeatInfo != null) {
                        redisHelper.renewalLock(heartBeatInfo.namespace, heartBeatInfo.key,
                                heartBeatInfo.lockUuid,
                                heartBeatInfo.heartbeatExpireSecond);
                    }
                }
                long cost = System.currentTimeMillis() - start;
                if (cost > 3000) {
                    LOGGER.warn("total heartbeat renewal cost:{}ms > 3000ms, please report at github/pugwoo/redis-helper", cost);
                }

                // 3秒heart beat一次，这里是fixed delay，已经考虑到3秒对于30秒的默认超时时长，已经足够了
                long sleep = 3000 - cost;
                if (sleep < 0) {
                    sleep = 0;
                }
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
    }

    /**
     * 生成缓存最终的
     * key
     */
    private String generateNamespace(Method method) {
        return ClassUtils.getMethodSignatureWithClassName(method);
    }

}
