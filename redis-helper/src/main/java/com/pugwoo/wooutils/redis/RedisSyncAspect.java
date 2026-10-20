package com.pugwoo.wooutils.redis;

import com.pugwoo.wooutils.redis.exception.NotGetLockException;
import com.pugwoo.wooutils.redis.impl.JsonRedisObjectConverter;
import com.pugwoo.wooutils.utils.ClassUtils;
import com.pugwoo.wooutils.utils.InnerCommonUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.mvel2.MVEL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@EnableAspectJAutoProxy
@Aspect
@Order(2000)
public class RedisSyncAspect implements InitializingBean, DisposableBean, AutoCloseable {

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

    /**
     * 心跳续约线程由本实例持有（不再用 static 字段）。旧实现每次 afterPropertiesSet 都新建一条
     * static 线程并覆盖字段引用，旧线程永不停止、还持有旧实例及其 RedisHelper 无法被 GC；
     * 实例持有后，destroy()/shutdown() 就能精确停止自己启动的这一条。多上下文并存时，
     * 每条存活实例的心跳线程都会续约静态 heartBeatKeys 中的全部锁，任一实例销毁只停自己的线程。
     */
    private volatile HeartbeatRenewalTask heartbeatRenewalTask = null;

    private final long startTimestamp = System.currentTimeMillis();

    /**shutdown() 是否已经执行过，保证 destroy()/close()/shutdown() 多路回调幂等*/
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);

    private final ThreadPoolExecutor redisSyncHeartbeatRenewalThreadPool =
            InnerCommonUtils.createThreadPool(1, 10000, 1,
                    "Redis-Sync-Heartbeat-Renewal-Thread-Pool", Thread.MAX_PRIORITY);

    @Override
    public void afterPropertiesSet() {
        long cost = System.currentTimeMillis() - startTimestamp;
        if (redisHelper == null) {
            LOGGER.error("redisHelper is null, RedisSyncAspect will pass through all method call");
        } else if (shutdownInitiated.get()) {
            LOGGER.warn("RedisSyncAspect is already shutdown, heartbeat renewal thread will not start");
        } else {
            synchronized (this) {
                if (heartbeatRenewalTask != null && heartbeatRenewalTask.isAlive()) {
                    // 已经有一条心跳线程在运行，重复初始化不再新建，避免线程累积
                    return;
                }
                heartbeatRenewalTask = new HeartbeatRenewalTask();
                heartbeatRenewalTask.setName("RedisSyncAspect-heartbeat-renewal-thread");
                heartbeatRenewalTask.setPriority(Thread.MAX_PRIORITY);
                heartbeatRenewalTask.start();
            }
            LOGGER.info("@Synchronized init success, cost:{} ms.", cost);
        }
    }

    @Override
    public void destroy() {
        shutdown();
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * 停止本实例的心跳续约线程和续约线程池。幂等：重复调用直接返回。
     * 业务线程在 shutdown 后若再加锁成功，putToHeatBeat 仍会往静态 heartBeatKeys 放入记录，
     * 但已关闭实例的心跳线程不再续约，该锁会按 heartbeatExpireSecond 自然过期——关闭后继续
     * 使用已销毁的 Spring bean 本就属误用，这里只保证线程停得掉、不报错刷屏。
     */
    public void shutdown() {
        if (!shutdownInitiated.compareAndSet(false, true)) {
            return;
        }
        HeartbeatRenewalTask task = heartbeatRenewalTask;
        heartbeatRenewalTask = null;
        if (task != null) {
            task.stopTask();
            if (Thread.currentThread() != task) {
                task.interrupt();
                try {
                    task.join(5000);
                    if (task.isAlive()) {
                        LOGGER.warn("RedisSyncAspect heartbeat renewal thread did not stop within 5 seconds, "
                                + "it will exit after current renewal round finishes");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        redisSyncHeartbeatRenewalThreadPool.shutdownNow();
        try {
            if (!redisSyncHeartbeatRenewalThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("RedisSyncAspect heartbeat renewal thread pool did not terminate within 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOGGER.info("RedisSyncAspect shutdown complete");
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
            // if redis down and allow pass
            if (param.passThroughWhenRedisDown && redisSyncRet.isRedisDown) {
                LOGGER.warn("redis is down and synchronized allow pass through when redis down, namespace:{} key:{}, so pass it", param.namespace, param.key);
            } else {
                return null;
            }
        }

        // if all redis lock got, then process method, otherwise get next redis lock
        try {
            if (params.size() - 1 == i) {
                RedisSyncContext.recordSuccessTotalLockCost(System.currentTimeMillis() - startTime);
                RedisSyncContext.set(true, true);
                return param.pjp.proceed();
            }
            return proceed(params, i + 1, startTime);
        } finally {
            if (!(param.passThroughWhenRedisDown && redisSyncRet.isRedisDown)) {
                releaseLock(param, redisSyncRet);
            }
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
        RedisSyncContext.setIsReleaseLockSuccess(result);
        logReleaseLock(p, redisSyncRet.lockUuid, result);
    }

    private RedisSyncRet tryGetLockWithRabbitSeries(RedisSyncParam p) throws Throwable {
        boolean isRedisDown = true;

        // 构造兔子数列
        int a = 0, b = 1;
        long start = System.currentTimeMillis();
        int tmpExpireSecond = p.expireSecond > 0 ? p.expireSecond : p.heartbeatExpireSecond;
        for (int i = 1; true; i++) {

            long lockStart = System.currentTimeMillis();
            String lockUuid = null;

            try {
                // 根据mode属性选择排它锁或共享锁
                if ("share".equalsIgnoreCase(p.mode)) {
                    lockUuid = redisHelper.requireShareLock(p.namespace, p.key, tmpExpireSecond, p.isReentrantLock, p.maxShareClients);
                } else {
                    lockUuid = redisHelper.requireLock(p.namespace, p.key, tmpExpireSecond, p.isReentrantLock);
                }
                isRedisDown = false;
            } catch (Throwable e) {
                LOGGER.error("get lock error, namespace:{}, key:{}, mode:{}", p.namespace, p.key, p.mode, e);
            }

            if (lockUuid != null) {
                // 获取到了锁，到这里之前发生了 GC或者获取锁的时候网络TTL大，导致锁过期了，则进行解锁并等待下一轮获取
                if (System.currentTimeMillis() - lockStart > tmpExpireSecond * 1000L) {
                    boolean result = redisHelper.releaseLock(p.namespace, p.key, lockUuid, p.isReentrantLock);
                    logReleaseLock(p, lockUuid, result);
                    return RedisSyncRet.notSuccessGetLock(System.currentTimeMillis() - start, i, isRedisDown);
                }

                logSuccessGetLock(p, lockUuid);

                String uuid = null;
                if (p.expireSecond <= 0) { // 此时是心跳机制
                    uuid = putToHeatBeat(p, lockUuid);
                }

                return RedisSyncRet.successGetLock(uuid, lockUuid, System.currentTimeMillis() - start, i, isRedisDown);
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
                return RedisSyncRet.notSuccessGetLock(System.currentTimeMillis() - start, i, isRedisDown);
            }

            long totalWait = System.currentTimeMillis() - start;
            if (totalWait >= p.waitLockMillisecond) {
                RedisSyncContext.set(true, false);
                if (p.logDebug) {
                    LOGGER.info("namespace:{},key:{}, give up getting a lock,total wait:{}ms,threadName:{}",
                            p.namespace, p.key, totalWait, Thread.currentThread().getName());
                }
                mayThrowExceptionIfNotGetLock(p.sync, p.targetMethod, p.namespace, p.key);
                return RedisSyncRet.notSuccessGetLock(System.currentTimeMillis() - start, i, isRedisDown);
            }
            if (p.waitLockMillisecond - totalWait < b) {
                try {
                    Thread.sleep(p.waitLockMillisecond - totalWait);
                } catch (InterruptedException ignored) {
                }
            } else {
                try {
                    Thread.sleep(b);
                } catch (InterruptedException ignored) {
                }
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
            String lockMode = "share".equalsIgnoreCase(p.mode) ? "share lock" : "exclusive lock";
            if (p.expireSecond > 0) {
                LOGGER.info("namespace:{},key:{},got {},expireSecond:{},lockUuid:{},threadName:{}",
                        p.namespace, p.key, lockMode, p.expireSecond, lockUuid, Thread.currentThread().getName());
            } else {
                LOGGER.info("namespace:{},key:{},got {},heartbeatExpireSecond:{},lockUuid:{},threadName:{}",
                        p.namespace, p.key, lockMode, p.heartbeatExpireSecond, lockUuid,
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
        redisSyncParam.passThroughWhenRedisDown = sync.passThroughWhenRedisDown();
        redisSyncParam.mode = sync.mode();
        redisSyncParam.maxShareClients = sync.maxShareClients();
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
            if (sync.customExceptionMessage() != null && !sync.customExceptionMessage().isEmpty()) {
                throw new NotGetLockException(targetMethod, namespace, key, sync.customExceptionMessage());
            } else {
                throw new NotGetLockException(targetMethod, namespace, key);
            }
        }
    }

    public void setRedisHelper(RedisHelper redisHelper) {
        this.redisHelper = redisHelper;
    }

    private class HeartbeatRenewalTask extends Thread {

        /**是否继续运行，stopTask() 后置 false，循环据此退出*/
        private volatile boolean running = true;

        void stopTask() {
            running = false;
        }

        boolean isRunning() {
            return running;
        }

        @Override
        public void run() {
            if (redisHelper == null) {
                LOGGER.error("redisHelper is null, HeartbeatRenewalTask stop");
                return;
            }

            while (running) {
                long start = System.currentTimeMillis();

                try {
                    List<Future<?>> futures = new ArrayList<>();
                    for (String key : heartBeatKeys.keySet()) {
                        if (!running) {
                            break;
                        }
                        Future<Boolean> future = redisSyncHeartbeatRenewalThreadPool.submit(() -> {
                            HeartBeatInfo heartBeatInfo = heartBeatKeys.get(key);
                            // 相当于double-check
                            if (heartBeatInfo != null) {
                                try {
                                    redisHelper.renewalLock(heartBeatInfo.namespace, heartBeatInfo.key,
                                            heartBeatInfo.lockUuid,
                                            heartBeatInfo.heartbeatExpireSecond);
                                } catch (Throwable e) {
                                    LOGGER.error("renewalLock throw exception, namespace:{}, key:{}, lockUuid:{}, heartbeatExpireSecond:{}",
                                            heartBeatInfo.namespace, heartBeatInfo.key, heartBeatInfo.lockUuid, heartBeatInfo.heartbeatExpireSecond, e);
                                }
                            }
                            return true; // 无论如何返回true
                        });
                        futures.add(future);
                    }
                    // 等待本批次的更新结束
                    if (!futures.isEmpty()) {
                        InnerCommonUtils.waitAllFuturesDone(futures);
                    }
                } catch (Throwable e) {
                    // 保证心跳线程存活，例如关闭时线程池已 shutdownNow 导致 submit 被拒绝；
                    // 关闭中（running 已为 false）则不再打日志
                    if (running) {
                        LOGGER.error("heartbeat renewal round fail", e);
                    }
                }

                long cost = System.currentTimeMillis() - start;
                if (running && cost > 3000) { // 增大线程池
                    int oldCorePoolSize = redisSyncHeartbeatRenewalThreadPool.getCorePoolSize();
                    int newCorePoolSize = oldCorePoolSize == 1 ? 10 : oldCorePoolSize * 2;
                    if (newCorePoolSize > 100) {
                        newCorePoolSize = 100; // 分布式锁刷新线程限制最大100条线程
                    }
                    redisSyncHeartbeatRenewalThreadPool.setMaximumPoolSize(newCorePoolSize); // MaximumPoolSize必须>=CorePoolSize
                    redisSyncHeartbeatRenewalThreadPool.setCorePoolSize(newCorePoolSize);
                    LOGGER.warn("total heartbeat renewal cost:{}ms > 3000ms, increment pool size from {} to {}", cost, oldCorePoolSize, newCorePoolSize);
                }

                // 3秒heart beat一次，这里是fixed delay，已经考虑到3秒对于30秒的默认超时时长，已经足够了
                long sleep = 3000 - cost;
                if (sleep < 0) {
                    sleep = 0;
                }
                if (sleep > 0 && running) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        if (!running) {
                            break;
                        }
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
