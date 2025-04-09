package com.pugwoo.wooutils.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.pugwoo.wooutils.redis.RedisHelper;
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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@EnableAspectJAutoProxy
@Aspect
@Order(1000)
public class HiSpeedCacheAspect implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(HiSpeedCacheAspect.class);

    /**
     * 因为ConcurrentHashMap不能存放null值，所以用这个特殊的String来代表null值，redis同理。<br>
     * 缓存null值是避免缓存穿透
     **/
    private static final String NULL_VALUE = "(NULL)HiSpeedCache@DpK3GovAptNICKAndKarenXSysudYrY";

    private final long startTimestamp = System.currentTimeMillis();

    @Autowired(required = false)
    private RedisHelper redisHelper;

    @Override
    public void afterPropertiesSet() {
        long cost = System.currentTimeMillis() - startTimestamp;
        if(redisHelper == null) {
            LOGGER.info("@HiSpeedCache init success, cost:{} ms.", cost);
        } else {
            LOGGER.info("@HiSpeedCache init success with redisHelper, cost:{} ms.", cost);
        }
    }

    // 多线程执行更新数据任务，默认十个线程
    private final ExecutorService executorService;

    public HiSpeedCacheAspect() {
        this(10);
    }

    /**
     * @param nUpdateThreads 指定数据任务的线程数
     */
    public HiSpeedCacheAspect(int nUpdateThreads) {
        this(nUpdateThreads, 0);
    }
    
    /**
     * @param nUpdateThreads 指定数据任务的线程数
     * @param dataMaxSize 存储数据对象的最大数量。大于0时生效；
     *                    当未限制存储对象最大数量时，使用ConcurrentHashMap进行存储，可以获得更好的性能；
     *                    当限制存储对象最大数量时，达到上限将使用LRU策略清理。
     */
    public HiSpeedCacheAspect(int nUpdateThreads, int dataMaxSize) {
        executorService = Executors.newFixedThreadPool(nUpdateThreads,
                new MyThreadFactory("HiSpeedCache-update-thread"));
        if (dataMaxSize > 0) {
            dataMap = Collections.synchronizedMap(new LinkedHashMap<String, Object>(dataMaxSize + 1, 1.0f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                    return size() > dataMaxSize;
                }
            });
        } else {
            dataMap = new ConcurrentHashMap<>();
        }
    }

    private static class ContinueFetchDTO {
        private volatile ProceedingJoinPoint pjp;
        private final HiSpeedCache hiSpeedCache;
        private volatile long expireTimestamp; // 此次调用的过时时间（毫秒时间戳）
        private final boolean cacheNullValue;

        private ContinueFetchDTO(ProceedingJoinPoint pjp, HiSpeedCache hiSpeedCache, long expireTimestamp, boolean cacheNullValue) {
            this.pjp = pjp;
            this.hiSpeedCache = hiSpeedCache;
            this.expireTimestamp = expireTimestamp;
            this.cacheNullValue = cacheNullValue;
        }
    }

    // 特别注意，因为expireLineMap和fetchLineMap不是线程安全，下面实现对其进行了synchronized，已经确认之间没有循环加锁，避免掉死锁的可能

    private final Map<String, Object> dataMap;                                // 存缓存数据的
    private final Map<Long, List<String>> expireLineMap = new TreeMap<>();    // 存数据超时时间的，超时时间 -> 对应于该超时时间的key的列表
    private final Map<String, Long> keyExpireMap = new ConcurrentHashMap<>(); // 每个key的超时时间，key -> 超时时间

    private final Map<String, ContinueFetchDTO> keyContinueFetchMap = new ConcurrentHashMap<>(); // 每个key持续更新的信息
    private final Map<Long, List<String>> fetchLineMap = new TreeMap<>();     // 持续获取的时间线，里面只有每个key的最近一次获取时间

    private volatile CleanExpireDataTask cleanThread = null;   // 不需要多线程
    private volatile ContinueUpdateTask continueThread = null; // 不需要多线程

    private final Map<String, Boolean> concurrentFetchControl = new ConcurrentHashMap<>(); // 控制fetch的并发执行

    @Around("@annotation(com.pugwoo.wooutils.cache.HiSpeedCache) execution(* *.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HiSpeedCacheContext.setHiSpeedCacheAspect(this);

        boolean disable = HiSpeedCacheContext.getDisable();
        if (disable) {
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method targetMethod = signature.getMethod();
        HiSpeedCache hiSpeedCache = targetMethod.getAnnotation(HiSpeedCache.class);

        // 确定是否走缓存
        boolean cacheCondition = evalCacheConditionScript(pjp, hiSpeedCache);
        if (!cacheCondition) {
            return pjp.proceed();
        }

        boolean useRedis = checkUseRedis(hiSpeedCache);

        ParameterizedType type = null;
        Type genericReturnType = targetMethod.getGenericReturnType();
        if (genericReturnType instanceof ParameterizedType) { // 当返回参数有泛型时，才会是这个类型
            type = (ParameterizedType) genericReturnType;
        }

        String key;
        try {
            key = generateKey(pjp, hiSpeedCache);
        } catch (Throwable e) {
            LOGGER.error("eval keyScript fail, keyScript:{}, args:{}, HiSpeedCache is disabled for this call.",
                    hiSpeedCache.keyScript(), JsonRedisObjectConverter.toJson(pjp.getArgs()));
            return pjp.proceed(); // 出现异常则等价于不使用缓存，直接调方法
        }
        String cacheKey = generateCacheKey(targetMethod, key);

        renewFetchExpire(pjp, hiSpeedCache, cacheKey);
        
        // 缓存到本地的配置
        int cacheRedisDataMillisecond = hiSpeedCache.cacheRedisDataMillisecond();
        boolean cacheRedisData = cacheRedisDataMillisecond > 0;

        boolean cacheNullValue = hiSpeedCache.cacheNullValue();

        boolean forceRefresh = HiSpeedCacheContext.getForceRefresh();
        boolean tryForceRefresh = HiSpeedCacheContext.getTryForceRefresh();
        boolean isTryForceRefreshSuccess = false;

        Object ret = null;
        if (tryForceRefresh) {
            try {
                ret = pjp.proceed();
                isTryForceRefreshSuccess = ret != null || cacheNullValue; // 标记缓存null值，说明null是有意义的，可以认为调用成功
            } catch (Throwable e) {
                LOGGER.error("tryForceRefresh fail, key:{}, args:{}, method:{} HiSpeedCache will use cache.",
                        cacheKey, JsonRedisObjectConverter.toJson(pjp.getArgs()), targetMethod, e);
            }
        }

        // 这两个变量用于存放redis中缓存的且已经过期的数据，如果本地调用失败，可以降级为redis中缓存的数据
        boolean isRedisHaveData = false;
        Object redisCachedValue = null;

        if (!forceRefresh && !isTryForceRefreshSuccess) { // 非强制刷新，才走缓存逻辑
            // 查看数据是否有命中，有则直接返回
            if(useRedis) {
                // 如果设置了缓存redis数据到本地，则尝试获取本地缓存数据
                if (cacheRedisData) {
                    Object cacheData = getCacheData(cacheKey);
                    if (cacheData != null) {
                        return NULL_VALUE.equals(cacheData) ? null : processClone(hiSpeedCache, cacheData, type);
                    }
                }

                RedisCacheDTO redisCache = getRedisCache(cacheKey);
                if (redisCache.isRedisOk()) {
                    String value = redisCache.getValue();
                    if(value != null) { // == null则缓存没命中，应该走下面调用逻辑
                        Object result = NULL_VALUE.equals(value) ? null : parseJson(value, targetMethod, type);

                        // redis放的是有效数据
                        if (redisCache.getExpireTimestamp() == null || redisCache.getExpireTimestamp() > System.currentTimeMillis()) {
                            if (cacheRedisData) { // 缓存到本地
                                putCacheData(cacheKey, result == null ? NULL_VALUE : result,
                                        cacheRedisDataMillisecond + System.currentTimeMillis());
                            }
                            return processClone(hiSpeedCache, result, type);
                        } // else 走直接调用
                        isRedisHaveData = true;
                        redisCachedValue = result;
                    }
                } else { // redis有故障，尝试本地缓存
                    Object cacheData = getCacheData(cacheKey);
                    if (cacheData != null) {
                        return NULL_VALUE.equals(cacheData) ? null : processClone(hiSpeedCache, cacheData, type);
                    } // else 走直接调用
                }
            } else {
                Object cacheData = getCacheData(cacheKey);
                if (cacheData != null) {
                    return NULL_VALUE.equals(cacheData) ? null : processClone(hiSpeedCache, cacheData, type);
                } // else 走直接调用
            }
        }

        // 强制刷新或缓存没有命中时，走下面的逻辑
        if (!isTryForceRefreshSuccess) {
            // 没有强制刷新的情况下，如果调用业务逻辑异常，同时还有redis过期缓存时，那么fallback为redis的缓存
            if (!forceRefresh && isRedisHaveData) {
                try {
                    ret = pjp.proceed();
                } catch (Throwable e) {
                    LOGGER.error("call biz method fail, fallback to redis cache, key:{}", cacheKey, e);
                    return redisCachedValue; // 一次性使用，也不会将这个值重新刷到缓存中，这里不需要processClone
                }
            } else {
                ret = pjp.proceed();
            }
        }

        boolean continueFetch = hiSpeedCache.continueFetchSecond() > 0;
        
        // 结果为null 不缓存 没有自动刷新缓存 则直接返回
        if (ret == null && !cacheNullValue && !continueFetch) {
            return null;
        }

        synchronized (HiSpeedCacheAspect.class) {
            int expireSecond = Math.max(hiSpeedCache.expireSecond(), hiSpeedCache.continueFetchSecond());
            long expireTime = expireSecond * 1000L + System.currentTimeMillis();

            if(useRedis) {
                if(ret != null) {
                    boolean setRedisSuccess = setRedisCache(cacheKey, ret, hiSpeedCache.expireSecond(), hiSpeedCache.continueFetchSecond());
                    if (setRedisSuccess) {
                        if (cacheRedisData) {
                            putCacheData(cacheKey, ret, cacheRedisDataMillisecond + System.currentTimeMillis());
                        }
                    } else { // 当redis发生故障时，降级为内存缓存
                        putCacheData(cacheKey, ret, expireTime);
                    }
                } else if (cacheNullValue) {
                    boolean setRedisSuccess = setRedisCache(cacheKey, NULL_VALUE,
                            hiSpeedCache.expireSecond(), hiSpeedCache.continueFetchSecond()); // 缓存null值
                    if (setRedisSuccess) {
                        if (cacheRedisData) {
                            putCacheData(cacheKey, NULL_VALUE, cacheRedisDataMillisecond + System.currentTimeMillis());
                        }
                    } else { // 当redis发生故障时，降级为内存缓存
                        putCacheData(cacheKey, NULL_VALUE, expireTime);
                    }
                }
            } else {
                if(ret != null) {
                    putCacheData(cacheKey, ret, expireTime);
                } else if (cacheNullValue) {
                    putCacheData(cacheKey, NULL_VALUE, expireTime);
                }
            }

            if (continueFetch) {
                ContinueFetchDTO continueFetchDTO = new ContinueFetchDTO(pjp, hiSpeedCache, expireTime, cacheNullValue);
                keyContinueFetchMap.put(cacheKey, continueFetchDTO);
                long nextFetchTime = Math.min(hiSpeedCache.expireSecond(), hiSpeedCache.continueFetchSecond())
                        * 1000L + System.currentTimeMillis();
                addFetchToTimeLine(nextFetchTime, cacheKey);
            }
        }

        startThread(hiSpeedCache);

        // 如果用了redis且没有用cacheRedisData，则不需要克隆
        if (useRedis && !cacheRedisData) {
            return ret;
        } else {
            return processClone(hiSpeedCache, ret, type);
        }
    }

    /**
     * 获取高速缓存的运行统计信息
     */
    public HiSpeedCacheStatisticDTO getStatistic() {
        HiSpeedCacheStatisticDTO statisticDTO = new HiSpeedCacheStatisticDTO();
        statisticDTO.setCacheDataCount(dataMap.size());
        return statisticDTO;
    }

    /**启用清理线程和更新线程*/
    private void startThread(HiSpeedCache hiSpeedCache) {
        if (cleanThread == null) {
            synchronized (CleanExpireDataTask.class) {
                if (cleanThread == null) {
                    cleanThread = new CleanExpireDataTask();
                    cleanThread.setName("HiSpeedCache-clean-thread");
                    cleanThread.start();
                }
            }
        }

        if (hiSpeedCache.continueFetchSecond() != 0) {
            if (continueThread == null) {
                synchronized (ContinueUpdateTask.class) {
                    if (continueThread == null) {
                        continueThread = new ContinueUpdateTask();
                        continueThread.setName("HiSpeedCache-update-thread");
                        continueThread.start();
                    }
                }
            }
        }
    }

    /**续期持续fetch的到期时间*/
    private void renewFetchExpire(ProceedingJoinPoint pjp, HiSpeedCache hiSpeedCache, String cacheKey) {
        int fetchSecond = hiSpeedCache.continueFetchSecond();
        if(fetchSecond > 0) { // 持续更新时，每次接口的访问都会延长持续获取的时长(如果还没超时的话)
            ContinueFetchDTO continueFetchDTO = keyContinueFetchMap.get(cacheKey);
            if(continueFetchDTO != null) {
                continueFetchDTO.pjp = pjp;
                continueFetchDTO.expireTimestamp = fetchSecond * 1000L + System.currentTimeMillis();
            }
        }
    }

    /**生成缓存最终的key*/
    private String generateCacheKey(Method targetMethod, String key) {
        String methodSignatureWithClassName = ClassUtils.getMethodSignatureWithClassName(targetMethod);
        return "HSC:" + methodSignatureWithClassName + (key.isEmpty() ? "" : ":" + key);
    }

    private String getCacheConfigKey(String cacheKey) {
        return "HSCA:" + cacheKey.substring(4);
    }

    private String generateKey(ProceedingJoinPoint pjp, HiSpeedCache hiSpeedCache) {
        String key = "";

        String keyScript = hiSpeedCache.keyScript();
        if (InnerCommonUtils.isNotBlank(keyScript)) {
            Map<String, Object> context = new HashMap<>();
            context.put("args", pjp.getArgs()); // 类型是Object[]

            Object result = MVEL.eval(keyScript, context);
            if (result != null) { // 返回结果为null等价于keyScript为空字符串
                key = result.toString();
            }
        } else {
            // 当keyScript没有设置，而方法的参数的个数又不是0个时，打印告警日志，这种情况一般是有问题的
            if (pjp.getArgs() != null && pjp.getArgs().length > 0) {
                LOGGER.warn("HiSpeedCache keyScript is empty, while method args is not empty, method:{}, class:{}",
                        ((MethodSignature) pjp.getSignature()).getMethod().getName(),
                        pjp.getTarget().getClass().getName());
            }
        }

        return key;
    }

    private boolean evalCacheConditionScript(ProceedingJoinPoint pjp, HiSpeedCache hiSpeedCache) {
        String cacheConditionScript = hiSpeedCache.cacheConditionScript();
        if (InnerCommonUtils.isNotBlank(cacheConditionScript)) {
            Map<String, Object> context = new HashMap<>();
            context.put("args", pjp.getArgs()); // 类型是Object[]

            try {
                Object result = MVEL.eval(cacheConditionScript, context);
                return (result instanceof Boolean) && (Boolean) result;
            } catch (Exception e) {
                LOGGER.error("HiSpeedCache cacheConditionScript eval error, method:{}, script:{}",
                        ((MethodSignature) pjp.getSignature()).getMethod().getName(), cacheConditionScript, e);
                return false;
            }
        } else {
            return true;
        }
    }

    private boolean checkUseRedis(HiSpeedCache hiSpeedCache) {
        boolean useRedis = false;
        if(hiSpeedCache.useRedis()) {
            if(redisHelper != null) {
                useRedis = true;
            } else {
                LOGGER.error("HiSpeedCache config useRedis=true, while there is no redisHelper");
            }
        }
        return useRedis;
    }

    /**
     * 获取缓存数据 会进行过期校验
     * @param cacheKey 缓存key
     * @return 缓存的值
     */
    private Object getCacheData(String cacheKey) {
        Object data = dataMap.get(cacheKey);
        if (data == null) { return null; }
        
        // 如果超过过期时间，视为数据无效了; 其数据交由清理线程处理
        Long expire = keyExpireMap.get(cacheKey);
        if (expire == null || expire <= System.currentTimeMillis()) {
            return null;
        }
        return data;
    }
    
    /**
     * 缓存数据
     * @param cacheKey 缓存的key
     * @param cacheData 缓存的数据
     * @param expireTimestamp 过期时间戳 毫秒
     */
    private void putCacheData(String cacheKey, Object cacheData, long expireTimestamp) {
        dataMap.put(cacheKey, cacheData);
        changeKeyExpireTime(cacheKey, expireTimestamp);
    }

    /**解析json为object*/
    private Object parseJson(String value, Method targetMethod, ParameterizedType type) {
        Class<?> returnClazz = targetMethod.getReturnType();

        if (type == null) {
            return JsonRedisObjectConverter.parse(value, returnClazz);
        } else {
            return JsonRedisObjectConverter.parse(value, type);
        }
    }
    
    /*处理结果值克隆的问题*/
    private Object processClone(HiSpeedCache hiSpeedCache, Object data, ParameterizedType type) {
        if(data == null) {
            return null;
        }
        if(hiSpeedCache.cloneReturn()) {
            Class<?> clazz = data.getClass();
            // 基础类型、String不需要转，相当于性能优化
            if(clazz == String.class || clazz == Integer.class || clazz == Long.class) {
                return data;
            }

            // 如果有提供自定义的快速克隆方法，就使用自定义的
            Class<?> customCloner = hiSpeedCache.customCloner();
            if (customCloner == null || customCloner == void.class) {
                if (type == null) {
                    return JsonRedisObjectConverter.parse(JsonRedisObjectConverter.toJson(data), clazz);
                } else {
                    return JsonRedisObjectConverter.parse(JsonRedisObjectConverter.toJson(data), type);
                }
            } else {
                try {
                    CustomCloner cc = (CustomCloner) customCloner.newInstance();
                    return cc.clone(data);
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            return data;
        }
    }

    /**设置或修改cacheKey的超时时间，保证一个cacheKey只有一个超时时间*/
    private void changeKeyExpireTime(String cacheKey, long expireTime) {
        synchronized (expireLineMap) {
            Long oldExpireTime = keyExpireMap.get(cacheKey);
            if (oldExpireTime != null) { // 清理可能的老数据
                List<String> keys = expireLineMap.get(oldExpireTime);
                if(keys != null) {
                    int keysSize = keys.size();
                    if(keysSize == 0 || keysSize == 1) {
                        expireLineMap.remove(oldExpireTime);
                    } else {
                        keys.removeIf(o -> o == null || o.equals(cacheKey));
                    }
                }
            }

            // 设置进去新的超时时间
            keyExpireMap.put(cacheKey, expireTime);
            List<String> keys = expireLineMap.get(expireTime);
            if(keys == null) {
                keys = new ArrayList<>();
                keys.add(cacheKey);
                expireLineMap.put(expireTime, keys);
            } else {
                if(!keys.contains(cacheKey)) {
                    keys.add(cacheKey);
                }
            }
        }
    }

    /**将某个cacheKey的下一次获取加入到更新时间线中*/
    private void addFetchToTimeLine(long nextTime, String cacheKey) {
        synchronized (fetchLineMap) {

            // 检查一下cacheKey是否已经存在于刷新线中，如果已经存在，则不再加入
            for(Map.Entry<Long, List<String>> e : fetchLineMap.entrySet()) {
                if(e.getValue() != null) {
                    for(String ck : e.getValue()) {
                        if(ck != null && ck.equals(cacheKey)) {
                            return; // 不加入
                        }
                    }
                }
            }

            List<String> keys = fetchLineMap.get(nextTime);
            if(keys == null) {
                keys = new ArrayList<>();
                keys.add(cacheKey);
                fetchLineMap.put(nextTime, keys);
            } else {
                if(!keys.contains(cacheKey)) {
                    keys.add(cacheKey);
                }
            }
        }
    }

    /**
     * 从expireLineMap中，按超时顺序遍历，如果超时时间小于当前时间，则清理该key对应的List(String)列表的key dataMap
     * 所以这里操作了dataMap和expireLineMap两个map
     * 对于超时时间大于当前时间的，不处理
     */
    private void cleanExpireData() {
        synchronized (expireLineMap) {

            List<Long> removeList = new ArrayList<>();
            for (Map.Entry<Long, List<String>> entry : expireLineMap.entrySet()) {
                Long key = entry.getKey();
                if (key <= System.currentTimeMillis()) {
                    entry.getValue().forEach(cacheKey -> {
                        dataMap.remove(cacheKey);
                        keyExpireMap.remove(cacheKey);
                    });
                    removeList.add(key);
                } else {
                    break;
                }
            }

            removeList.forEach(expireLineMap::remove);
        }
    }

    /**
     * 持续调用刷新数据
     */
    private void refreshResult() {
        synchronized (fetchLineMap) {

            List<Long> removeList = new ArrayList<>();

            List<Long> addFetchToTimeLine_time = new ArrayList<>();
            List<String> addFetchToTimeLine_cacheKey = new ArrayList<>();

            for (Map.Entry<Long, List<String>> entry : fetchLineMap.entrySet()) {
                Long key = entry.getKey();
                if (key <= System.currentTimeMillis()) {
                    entry.getValue().forEach(cacheKey -> {
                        ContinueFetchDTO continueFetchDTO = keyContinueFetchMap.get(cacheKey);
                        if(continueFetchDTO == null) {
                            return;
                        }
                        // 安排下一次调用
                        long nextTime = continueFetchDTO.hiSpeedCache.expireSecond() * 1000L + System.currentTimeMillis();

                        // 多线程执行更新任务
                        executorService.submit(new Runnable() {
                            @Override
                            public void run() {
                                boolean isNeedRemoveConcurrent = false;
                                try {
                                    if (!continueFetchDTO.hiSpeedCache.concurrentFetch()) { // 串行执行
                                        Boolean result = concurrentFetchControl.putIfAbsent(cacheKey, true);
                                        if (result != null) { // 已经存在，说明在执行了
                                            LOGGER.warn("call {} ignore because the previous call is still running", cacheKey);
                                            return;
                                        }
                                    }
                                    isNeedRemoveConcurrent = true;

                                    long start = System.currentTimeMillis();
                                    Object result = continueFetchDTO.pjp.proceed();
                                    long end = System.currentTimeMillis();

                                    // 如果方法的执行时间，超过了数据的超时时间，是不太正常的
                                    if (end - start >= continueFetchDTO.hiSpeedCache.expireSecond() * 1000L) {
                                        LOGGER.warn("call {} cost {} ms, more than expire second:{} second",
                                                cacheKey, end - start, continueFetchDTO.hiSpeedCache.expireSecond());
                                    }

                                    // 结果为null且不缓存null值
                                    if (result == null && !continueFetchDTO.cacheNullValue) {
                                        return;
                                    }
                                    
                                    if(continueFetchDTO.hiSpeedCache.useRedis()) {
                                        setRedisCache(cacheKey, result != null ? result : NULL_VALUE,
                                                continueFetchDTO.hiSpeedCache.expireSecond(), continueFetchDTO.hiSpeedCache.continueFetchSecond());
                                        // 特别说明：定时任务中的redis故障，不自动降级为内存缓存，等待重试
                                    } else {
                                        if(result != null) {
                                            dataMap.put(cacheKey, result);
                                        } else {
                                            dataMap.put(cacheKey, NULL_VALUE); // 因为concurrentHashMap不能放null
                                        }
                                        changeKeyExpireTime(cacheKey, Math.max(continueFetchDTO.expireTimestamp, nextTime));
                                    }
                                } catch (Throwable e) {
                                    LOGGER.error("refreshResult execute pjp fail, key:{}", cacheKey, e);
                                } finally {
                                    if (isNeedRemoveConcurrent) {
                                        concurrentFetchControl.remove(cacheKey);
                                    }
                                }
                            }
                        });

                        if(nextTime <= continueFetchDTO.expireTimestamp) { // 下一次调用还在超时时间内
                            addFetchToTimeLine_time.add(nextTime);
                            addFetchToTimeLine_cacheKey.add(cacheKey);
                        } else {
                            keyContinueFetchMap.remove(cacheKey); // 清理continueFetchDTO
                        }
                    });
                    removeList.add(key);
                } else {
                    break;
                }
            }

            removeList.forEach(fetchLineMap::remove);

            for(int i = 0; i < addFetchToTimeLine_time.size(); i++) {
                addFetchToTimeLine(addFetchToTimeLine_time.get(i), addFetchToTimeLine_cacheKey.get(i));
            }
        }
    }

    private class CleanExpireDataTask extends Thread {
        @Override
        public void run() {
            while (true) { // 一直循环，不会退出
                try {
                    cleanExpireData();
                } catch (Throwable e) { // 保证线程存活
                    LOGGER.error("clean expire data error", e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private class ContinueUpdateTask extends Thread {
        @Override
        public void run() {
            while (true) { // 一直循环，不会退出
                try {
                    refreshResult();
                } catch (Throwable e) { // 保证线程存活
                    LOGGER.error("refresh result error", e);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static class MyThreadFactory implements ThreadFactory {

        private final AtomicInteger count = new AtomicInteger(1);

        private final String threadNamePrefix;

        public MyThreadFactory(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, threadNamePrefix + "-" + count.getAndIncrement());
        }

    }

    // 即便使用了redis，高速缓存仍设计为对redis弱依赖，当redis故障时，直接走方法调用，并自动降级为内存缓存

    /**
     * 保存redis缓存数据结果
     */
    private static class RedisCacheDTO {

        private String value;

        /**缓存数据的过期时间*/
        private Long expireTimestamp;

        private boolean isRedisOk;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean isRedisOk() {
            return isRedisOk;
        }

        public void setRedisOk(boolean redisOk) {
            isRedisOk = redisOk;
        }

        public Long getExpireTimestamp() {
            return expireTimestamp;
        }

        public void setExpireTimestamp(Long expireTimestamp) {
            this.expireTimestamp = expireTimestamp;
        }
    }

    private RedisCacheDTO getRedisCache(String cacheKey) {
        RedisCacheDTO result = new RedisCacheDTO();
        result.setRedisOk(true);
        try {
            String cacheConfigKey = getCacheConfigKey(cacheKey);
            List<String> keys = new ArrayList<>();
            keys.add(cacheKey);
            keys.add(cacheConfigKey);
            List<String> values = redisHelper.getStrings(keys);
            if (values != null && !values.isEmpty() && values.get(0) != null) {
                result.setValue(values.get(0));
                // 尝试获得cacheKey过期时间
                if (values.size() > 1 && InnerCommonUtils.isNotBlank(values.get(1))) {
                    Map<String, Object> config = JsonRedisObjectConverter.parse(values.get(1), new TypeReference<Map<String, Object>>() {
                    });
                    Object et = config.get("et");
                    if (et != null) {
                        result.setExpireTimestamp(InnerCommonUtils.parseLong(et));
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.error("redis getString error, key:{}", cacheKey, e);
            result.setRedisOk(false);
        }
        return result;
    }

    private boolean setRedisCache(String cacheKey, Object value, int expireSecond, int continueFetchSecond) {
        String cacheConfigKey = getCacheConfigKey(cacheKey);
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("et", System.currentTimeMillis() + expireSecond * 1000L);
            redisHelper.setObject(cacheConfigKey, continueFetchSecond, config);

            if (NULL_VALUE.equals(value)) {
                return redisHelper.setString(cacheKey, continueFetchSecond, NULL_VALUE);
            } else {
                return redisHelper.setObject(cacheKey, continueFetchSecond, value);
            }
        } catch (Throwable e) {
            LOGGER.error("redis set error, key:{}", cacheKey, e);
            return false;
        }
    }

}
