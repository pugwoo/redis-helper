package com.pugwoo.wooutils;

import com.pugwoo.wooutils.cache.WithCacheDemoService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Date;
import java.util.List;

@ContextConfiguration(locations = {"classpath:applicationContext-context.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class TestHiSpeedCache {

    @Autowired
    private WithCacheDemoService withCacheDemoService;

    @Test
    public void testNoCache() throws Exception {
        long start = System.currentTimeMillis();
        String str = withCacheDemoService.getSomething();
        assert str.equals("hello");
        System.out.println(str + new Date());
        str = withCacheDemoService.getSomething();
        assert str.equals("hello");
        System.out.println(str + new Date());
        str = withCacheDemoService.getSomething();
        assert str.equals("hello");
        System.out.println(str + new Date());
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end- start) >= 9000;
        assert withCacheDemoService.getSomethingCount() == 3; // 实际也执行了3次
    }
    
    /** 缓存null值 */
    @Test
    public void testWithCache() throws Exception {
        Thread.sleep(15000); // 等待缓存过期，缓存的continueFetchSecond是10秒

        withCacheDemoService.resetSomethingWithCacheCount();

        long start = System.currentTimeMillis();
        String str = withCacheDemoService.getSomethingWithCache();
        assert str == null;
        System.out.println(str + new Date());
        str = withCacheDemoService.getSomethingWithCache(); // 这次调用就直接走了缓存
        assert str == null;
        System.out.println(str + new Date());
        str = withCacheDemoService.getSomethingWithCache(); // 这次调用就直接走了缓存
        assert str == null;
        System.out.println(str + new Date());
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 3000 && (end - start) < 3800;
        assert withCacheDemoService.getSomethingWithCacheCount() == 1;  // 目标方法实际只执行了一次
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
        Thread.sleep(3000);  // 17 second
        System.out.println(withCacheDemoService.getSomethingWithCacheCount());
        assert withCacheDemoService.getSomethingWithCacheCount() == 8;
        Thread.sleep(3000);  // 20 second
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
        assert (end - start) > 3000 && (end - start) < 3800;
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

}
