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
        assert (end- start) > 9000;
        assert withCacheDemoService.getSomethingCount() == 3; // 实际也执行了3次
    }

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
        assert (end - start) > 3000 && (end - start) < 3800;
        assert withCacheDemoService.getSomethingWithCacheCount() == 1;
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
        assert withCacheDemoService.getSomethingWithCacheCount() >= ((int)cost/1000) - 1;
    }

}
