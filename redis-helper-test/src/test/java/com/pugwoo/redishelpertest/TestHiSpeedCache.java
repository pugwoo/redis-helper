package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.cache.WithCacheDemoService;
import com.pugwoo.wooutils.json.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

@SpringBootTest
public class TestHiSpeedCache {

    @Autowired
    private WithCacheDemoService withCacheDemoService;

    @Test
    public void testNoCache() throws Exception {
        Integer somethingCount = withCacheDemoService.getSomethingCount();

        long start = System.currentTimeMillis();
        String str = withCacheDemoService.getSomething(3);
        assert str.equals("hello");

        str = withCacheDemoService.getSomething(3);
        assert str.equals("hello");

        str = withCacheDemoService.getSomething(3);
        assert str.equals("hello");
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 9000 && (end - start) <= 9100;
        assert withCacheDemoService.getSomethingCount() - somethingCount == 3; // 实际也执行了3次
    }
    
    /** 缓存null值 */
    @Test
    public void testWithCache() throws Exception {
        withCacheDemoService.resetSomethingWithCacheCount();

        long start = System.currentTimeMillis();
        String str = withCacheDemoService.getSomethingWithCache();
        assert str == null;

        str = withCacheDemoService.getSomethingWithCache(); // 这次调用就直接走了缓存
        assert str == null;
        str = withCacheDemoService.getSomethingWithCache(); // 这次调用就直接走了缓存
        assert str == null;
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 3000 && (end - start) < 3800;
        assert withCacheDemoService.getSomethingWithCacheCount() == 1;  // 目标方法实际只执行了一次
    }
    
    /** 缓存null值 */
    @Test
    public void testWithCache2() throws Exception {
        Thread.sleep(15000); // 等待缓存过期，缓存的continueFetchSecond是10秒
        
        withCacheDemoService.resetSomethingWithCacheCount();
        long start = System.currentTimeMillis();
        
        String str;
        // 100ms * 100 = 10s
        // 第一次调用sleep了3s
        for (int i = 0; i < 100; i++) {
            str = withCacheDemoService.getSomethingWithCache2();
            // System.out.println(str + " " + new Date());
            assert str == null;
            Thread.sleep(100);
        }
        Thread.sleep(20000); // sleep的时候后台一直在fetch数据
        long end = System.currentTimeMillis();
        
        // 共计 10s + 3s +20s = 33s
        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 33000 && (end - start) < 35900; // 这里由原来的34秒，放宽到35.9秒，因为网络延迟
        
        // String getSomethingWithCache is start    @ 2021-07-25 01:04:15
        // String getSomethingWithCache is executed @ 2021-07-25 01:04:18  第一次调用
        // String getSomethingWithCache is start    @ 2021-07-25 01:04:22
        // String getSomethingWithCache is executed @ 2021-07-25 01:04:25  第一次fetch
        // String getSomethingWithCache is start    @ 2021-07-25 01:04:26
        // String getSomethingWithCache is executed @ 2021-07-25 01:04:29  第二次fetch
        // String getSomethingWithCache is start    @ 2021-07-25 01:04:30
        // String getSomethingWithCache is executed @ 2021-07-25 01:04:33  第三次fetch
        // String getSomethingWithCache is start    @ 2021-07-25 01:04:34
        // String getSomethingWithCache is executed @ 2021-07-25 01:04:37  第四次fetch
        // String getSomethingWithCache is start    @ 2021-07-25 01:04:38
        // String getSomethingWithCache is executed @ 2021-07-25 01:04:41  第五次fetch
        System.out.println(withCacheDemoService.getSomethingWithCacheCount());
        assert withCacheDemoService.getSomethingWithCacheCount() == 6;
    }
    
    /** 不缓存null值 */
    @Test
    public void testWithNotCacheNullValue() throws Exception {
        Thread.sleep(15000); // 等待缓存过期，缓存的continueFetchSecond是10秒
        
        withCacheDemoService.resetSomethingWithCacheCount();
        
        long start = System.currentTimeMillis();
        String str = withCacheDemoService.getSomethingWithNotCacheNullValue();
        assert str == null;
        System.out.println(str + new Date());
        str = withCacheDemoService.getSomethingWithNotCacheNullValue(); // 这次调用不走缓存 目标方法被调用
        assert str == null;
        System.out.println(str + new Date());
        str = withCacheDemoService.getSomethingWithNotCacheNullValue(); // 这次调用不走缓存 目标方法被调用
        assert str == null;
        System.out.println(str + new Date());
        long end = System.currentTimeMillis();
        
        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 9000 && (end - start) < 9900;
        System.out.println(withCacheDemoService.getSomethingWithCacheCount());
        // second 0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 ...
        //   call 1       12       23        3                                             3times
        //  fetch             1        2        3        3        3                        5times max
        // 当前时间在 9~10 秒之间 调用执行了3次 continueFetch执行了两次
        assert withCacheDemoService.getSomethingWithCacheCount() == 5;
        
        Thread.sleep(2000);  // 11 second
        System.out.println(withCacheDemoService.getSomethingWithCacheCount());
        assert withCacheDemoService.getSomethingWithCacheCount() == 6;
        Thread.sleep(3000);  // 14 second
        System.out.println(withCacheDemoService.getSomethingWithCacheCount());
        assert withCacheDemoService.getSomethingWithCacheCount() == 7;
        Thread.sleep(3500);  // 17.5 second // 原来是sleep 3000，但是17秒过于精确，这里调成17.5秒
        System.out.println(withCacheDemoService.getSomethingWithCacheCount());
        assert withCacheDemoService.getSomethingWithCacheCount() == 8;
        Thread.sleep(2500);  // 20 second
        System.out.println(withCacheDemoService.getSomethingWithCacheCount());
        assert withCacheDemoService.getSomethingWithCacheCount() == 8;
        Thread.sleep(3000);  // 20 second
        System.out.println(withCacheDemoService.getSomethingWithCacheCount());
        assert withCacheDemoService.getSomethingWithCacheCount() == 8;
        Thread.sleep(3000);  // 24 second
        System.out.println(withCacheDemoService.getSomethingWithCacheCount());
        assert withCacheDemoService.getSomethingWithCacheCount() == 8;
    }

    @Test
    public void testSomethingWithCacheCloneReturn() throws Exception {
        long start = System.currentTimeMillis();
        Date date = withCacheDemoService.getSomethingWithCacheCloneReturn("hello");
        System.out.println(date + "," + new Date());
        date = withCacheDemoService.getSomethingWithCacheCloneReturn("hello");
        System.out.println(date + "," + new Date());
        date = withCacheDemoService.getSomethingWithCacheCloneReturn("hello");
        System.out.println(date + "," + new Date());
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 3000 && (end - start) < 3800;
    }

    @Test
    public void testSomethingWithRedis() throws Exception {
        long start = System.currentTimeMillis();
        List<Date> dates = withCacheDemoService.getSomethingWithRedis();
        System.out.println(dates + "," + new Date());
        dates = withCacheDemoService.getSomethingWithRedis();
        System.out.println(dates + "," + new Date());
        dates = withCacheDemoService.getSomethingWithRedis();
        assert dates.get(0) != null && dates.get(0) instanceof Date;
        System.out.println(dates + "," + new Date());
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) > 3000 && (end - start) < 3800;
    }

    @Test
    public void testLittleBenchmark() throws Exception {
        System.out.println("start at " + new Date());

        withCacheDemoService.getSomethingWithCache();
        withCacheDemoService.resetSomethingWithCacheCount();

        int times = 10000000;
        // 测试调用1000万次的时间
        long start = System.currentTimeMillis();
        for(int i = 0; i < times; i++) {
            withCacheDemoService.getSomethingWithCache();
        }
        long end = System.currentTimeMillis();

        System.out.println("end at " + new Date());

        long cost = (end - start);
        System.out.println("cost:" + cost + "ms");
        double qps = times / (cost / 1000.0);
        System.out.println("qps:" + qps);

        System.out.println("call count:" + withCacheDemoService.getSomethingWithCacheCount());
        assert qps > 100000;  // qps应该至少10万以上，正常都有100万
        assert withCacheDemoService.getSomethingWithCacheCount() <= ((int)cost/1000);
    }

    @Test
    public void testBenchmarkRedisCache() throws Exception {
        // 测试调用1000万次的时间
        int times = 10000000;
        long start = System.currentTimeMillis();
        for(int i = 0; i < times; i++) {
            withCacheDemoService.getSomethingWithRedis();
        }
        long end = System.currentTimeMillis();

        long cost = (end - start);
        System.out.println("cost:" + cost + "ms");
        double qps = times / (cost / 1000.0);
        System.out.println("qps:" + qps);

        assert qps > 300000;  // qps应该30万以上，如果不用cacheRedisDataMillisecond是不可能达到30万qps的
    }

    @Test
    public void testExpireTime() throws Exception {
        UUID.randomUUID(); // 第一次跑这个比较久，所以先预热

        // 这里大概运行20+秒，除了头和尾，大概应该有18次是cost在1000到1002之间，误差不会超过1毫秒（除了四舍五入）
        List<Long> costList = new ArrayList<>();

        long lastGetTime = System.currentTimeMillis();
        String lastUuid = withCacheDemoService.getRandomString();
        for (int i = 0; i < 10000; i++) {
            String uuid = withCacheDemoService.getRandomString();
            if (!uuid.equals(lastUuid)) { // uuid发生变化了，说明缓存失效了
                long cost = System.currentTimeMillis() - lastGetTime;
                System.out.println("cost:" + cost);
                costList.add(cost);

                lastGetTime = System.currentTimeMillis();
                lastUuid = uuid;
            }
            Thread.sleep(2);
        }

        // 至少18次1000或1001
        int count = 0;
        for (Long cost : costList) {
            if (cost == 1000 || cost == 1001 || cost == 1002) {
                count++;
            }
        }
        assert count >= 18;
    }

    /**测试clone & 泛型*/
    @Test
    public void testGeneric() throws Exception {
        for (int i = 0; i < 10; i++) {

            List<Date> dates = withCacheDemoService.getSomeDateWithCache();
            System.out.println(dates.get(0).getClass());
            System.out.println(JSON.toJson(dates));

            dates.forEach(o -> {
                assert o.getClass().equals(Date.class);
            });

            Thread.sleep(1000);
        }

        for (int i = 0; i < 10; i++) {
            Map<String, Date> dates = withCacheDemoService.getSomeDateWithCache2();
            System.out.println(JSON.toJson(dates));

            dates.forEach((k, v) -> {
                assert k.getClass().equals(String.class);
                assert v.getClass().equals(Date.class);
            });

            Thread.sleep(1000);
        }

    }

    @Test
    public void testWarningLog() {
        withCacheDemoService.withParam("hi");
    }
}
