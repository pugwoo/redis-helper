package com.pugwoo.redishelperbenchmark.cache;

import com.pugwoo.wooutils.cache.HiSpeedCache;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 这里测试的接口都sleep 3秒，故意制造比较慢的
 */
@Service
public class WithCacheDemoService {

    private AtomicInteger getSomething = new AtomicInteger(0);

    public String getSomething(int sleepSecond) throws Exception {
        getSomething.incrementAndGet();
        Thread.sleep(sleepSecond * 1000L);
        return "hello";
    }

    public Integer getSomethingCount() {
        return getSomething.get();
    }

    //////////////////////////////////////

    private AtomicInteger getSomethingWithCache = new AtomicInteger(0);

    @HiSpeedCache(expireSecond = 1, continueFetchSecond = 10, cloneReturn = false)
    public String getSomethingWithCache() throws Exception {
        getSomethingWithCache.incrementAndGet();
        Thread.sleep(3000);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("String getSomethingWithCache is executed @ " + df.format(new Date()));
        return null; // 测试缓存null值
    }
    
    @HiSpeedCache(expireSecond = 4, continueFetchSecond = 10,
            useRedis = true, cacheRedisDataMillisecond = 300, cloneReturn = false)
    public String getSomethingWithCache2() throws Exception {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("String getSomethingWithCache is start    @ " + df.format(new Date()));
        getSomethingWithCache.incrementAndGet();
        Thread.sleep(3000);
        System.out.println("String getSomethingWithCache is executed @ " + df.format(new Date()));
        return null;
    }
    
    @HiSpeedCache(expireSecond = 1, continueFetchSecond = 10, cacheNullValue = false, cloneReturn = false)
    public String getSomethingWithNotCacheNullValue() throws Exception {
        getSomethingWithCache.incrementAndGet();
        Thread.sleep(3000);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("String getSomethingWithCache is executed @ " + df.format(new Date()));
        return null; // 测试不缓存null值
    }

    public Integer getSomethingWithCacheCount() {
        return getSomethingWithCache.get();
    }

    public void resetSomethingWithCacheCount() {
        getSomethingWithCache.set(0);
    }


    /////////////////////////////////////

    @HiSpeedCache(continueFetchSecond = 10, cloneReturn = true, keyScript = "args[0]")
    public Date getSomethingWithCacheCloneReturn(String name) throws Exception {
        Thread.sleep(3000);
        return new Date();
    }

    @HiSpeedCache(continueFetchSecond = 10, useRedis = true, cacheRedisDataMillisecond = 100,
      cloneReturn = false) // 测试极端情况下，只缓存1毫秒，qps能达到8万
    public List<Date> getSomethingWithRedis() throws Exception {
        Thread.sleep(3000);
        List<Date> result = new ArrayList<>();
        result.add(new Date());
        result.add(new Date());

        return result;
    }

    // 支持克隆情况下的泛型
    @HiSpeedCache(continueFetchSecond = 10, cloneReturn = true)
    public List<Date> getSomeDateWithCache() throws Exception {
        Thread.sleep(3000);
        List<Date> dates = new ArrayList<>();
        dates.add(new Date());
        dates.add(new Date());
        dates.add(new Date());
        return dates;
    }

    // 支持克隆情况下的泛型
    @HiSpeedCache(continueFetchSecond = 10, cloneReturn = true)
    public Map<String, Date> getSomeDateWithCache2() throws Exception {
        Thread.sleep(3000);
        Map<String, Date> map = new HashMap<>();
        map.put("11", new Date());
        map.put("22", new Date());
        map.put("33", new Date());
        return map;
    }

    // 用于测试超时时间是否准时
    @HiSpeedCache(expireSecond = 1, cloneReturn = false)
    public String getRandomString() {
        return UUID.randomUUID().toString();
    }

    // 有参数，但是keyScript为空，应该打印出log告警
    @HiSpeedCache(expireSecond = 10, keyScript = "")
    public String withParam(String param) {
        return param;
    }
}
