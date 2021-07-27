package com.pugwoo.wooutils.cache;

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

    @HiSpeedCache(expireSecond = 1, continueFetchSecond = 10)
    public String getSomethingWithCache() throws Exception {
        getSomethingWithCache.incrementAndGet();
        Thread.sleep(3000);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("String getSomethingWithCache is executed @ " + df.format(new Date()));
        return null; // 测试缓存null值
    }
    
    @HiSpeedCache(expireSecond = 4, continueFetchSecond = 10, useRedis = true, cacheRedisDataMillisecond = 300)
    public String getSomethingWithCache2() throws Exception {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("String getSomethingWithCache is start    @ " + df.format(new Date()));
        getSomethingWithCache.incrementAndGet();
        Thread.sleep(3000);
        System.out.println("String getSomethingWithCache is executed @ " + df.format(new Date()));
        return null;
    }
    
    @HiSpeedCache(expireSecond = 1, continueFetchSecond = 10, cacheNullValue = false)
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

    @HiSpeedCache(continueFetchSecond = 10, useRedis = true, genericClass1 = Date.class,
        cacheRedisDataMillisecond = 100)
    public List<Date> getSomethingWithRedis() throws Exception {
        Thread.sleep(3000);
        List<Date> result = new ArrayList<>();
        result.add(new Date());
        result.add(new Date());

        return result;
    }

    // 支持克隆情况下的泛型
    @HiSpeedCache(continueFetchSecond = 10, cloneReturn = true, genericClass1 = Date.class)
    public List<Date> getSomeDateWithCache() throws Exception {
        Thread.sleep(3000);
        List<Date> dates = new ArrayList<>();
        dates.add(new Date());
        dates.add(new Date());
        dates.add(new Date());
        return dates;
    }

    // 支持克隆情况下的泛型
    @HiSpeedCache(continueFetchSecond = 10, cloneReturn = true,
            genericClass1 = String.class, genericClass2 = Date.class)
    public Map<String, Date> getSomeDateWithCache2() throws Exception {
        Thread.sleep(3000);
        Map<String, Date> map = new HashMap<>();
        map.put("11", new Date());
        map.put("22", new Date());
        map.put("33", new Date());
        return map;
    }
}
