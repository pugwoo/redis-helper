package com.pugwoo.redishelpertest.common;

import com.pugwoo.redishelpertest.cache.WithCacheDemoService;
import com.pugwoo.wooutils.cache.HiSpeedCacheAspect;
import com.pugwoo.wooutils.cache.HiSpeedCacheContext;
import com.pugwoo.wooutils.cache.HiSpeedCacheStatisticDTO;
import com.pugwoo.wooutils.json.JSON;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TestHiSpeedCacheAbstract {

    public abstract WithCacheDemoService getWithCacheDemoService();

    @Test
    public void testNoCache() throws Exception {
        Integer somethingCount = getWithCacheDemoService().getSomethingCount();

        long start = System.currentTimeMillis();
        String str = getWithCacheDemoService().getSomething(3);
        assert str.equals("hello");

        str = getWithCacheDemoService().getSomething(3);
        assert str.equals("hello");

        str = getWithCacheDemoService().getSomething(3);
        assert str.equals("hello");
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 9000 && (end - start) <= 9100;
        assert getWithCacheDemoService().getSomethingCount() - somethingCount == 3; // 实际也执行了3次
    }
    
    /** 缓存null值 */
    @Test
    public void testWithCache() throws Exception {
        getWithCacheDemoService().resetSomethingWithCacheCount();

        long start = System.currentTimeMillis();
        String str = getWithCacheDemoService().getSomethingWithCache();
        assert str == null;

        str = getWithCacheDemoService().getSomethingWithCache(); // 这次调用就直接走了缓存
        assert str == null;
        str = getWithCacheDemoService().getSomethingWithCache(); // 这次调用就直接走了缓存
        assert str == null;
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 3000 && (end - start) < 3800;
        assert getWithCacheDemoService().getSomethingWithCacheCount() == 1;  // 目标方法实际只执行了一次
    }
    
    /** 缓存null值 */
    @Test
    public void testWithCache2() throws Exception {
        Thread.sleep(15000); // 等待缓存过期，缓存的continueFetchSecond是10秒
        
        getWithCacheDemoService().resetSomethingWithCacheCount();
        long start = System.currentTimeMillis();
        
        String str;
        // 100ms * 100 = 10s
        // 第一次调用sleep了3s
        for (int i = 0; i < 100; i++) {
            str = getWithCacheDemoService().getSomethingWithCache2();
            // System.out.println(str + " " + new Date());
            assert str == null;
            Thread.sleep(100);
        }
        Thread.sleep(20000); // sleep的时候后台一直在fetch数据
        long end = System.currentTimeMillis();
        
        // 共计 10s + 3s +20s = 33s
        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 33000 && (end - start) < 40000; // 这里由原来的34秒，放宽到40秒，因为网络延迟
        
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
        int count = getWithCacheDemoService().getSomethingWithCacheCount();
        System.out.println("testWithCache2 count:" + count);
        assert (count >= 6 && count <= 9); // 由于提前刷新功能开启，刷新次数从8次改成9次
    }
    
    /** 不缓存null值 */
    @Test
    public void testWithNotCacheNullValue() throws Exception {
        Thread.sleep(15000); // 等待缓存过期，缓存的continueFetchSecond是10秒
        
        getWithCacheDemoService().resetSomethingWithCacheCount();
        
        long start = System.currentTimeMillis();
        String str = getWithCacheDemoService().getSomethingWithNotCacheNullValue();
        assert str == null;
        System.out.println(str + new Date());
        str = getWithCacheDemoService().getSomethingWithNotCacheNullValue(); // 这次调用不走缓存 目标方法被调用
        assert str == null;
        System.out.println(str + new Date());
        str = getWithCacheDemoService().getSomethingWithNotCacheNullValue(); // 这次调用不走缓存 目标方法被调用
        assert str == null;
        System.out.println(str + new Date());
        long end = System.currentTimeMillis();
        
        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 9000 && (end - start) < 9900;
        System.out.println(getWithCacheDemoService().getSomethingWithCacheCount());
        // second 0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 ...
        //   call 1       12       23        3                                             3times
        //  fetch             1        2        3        3        3                        5times max
        // 当前时间在 9~10 秒之间 调用执行了3次 continueFetch执行了两次
        assert getWithCacheDemoService().getSomethingWithCacheCount() == 5;
        
        Thread.sleep(2000);  // 11 second
        System.out.println(getWithCacheDemoService().getSomethingWithCacheCount());
        assert getWithCacheDemoService().getSomethingWithCacheCount() == 6;
        Thread.sleep(3000);  // 14 second
        System.out.println(getWithCacheDemoService().getSomethingWithCacheCount());
        assert getWithCacheDemoService().getSomethingWithCacheCount() == 7;
        Thread.sleep(3500);  // 17.5 second // 原来是sleep 3000，但是17秒过于精确，这里调成17.5秒
        System.out.println(getWithCacheDemoService().getSomethingWithCacheCount());
        assert getWithCacheDemoService().getSomethingWithCacheCount() == 8;
        Thread.sleep(2500);  // 20 second
        System.out.println(getWithCacheDemoService().getSomethingWithCacheCount());
        assert getWithCacheDemoService().getSomethingWithCacheCount() == 8;
        Thread.sleep(3000);  // 20 second
        System.out.println(getWithCacheDemoService().getSomethingWithCacheCount());
        assert getWithCacheDemoService().getSomethingWithCacheCount() == 8;
        Thread.sleep(3000);  // 24 second
        System.out.println(getWithCacheDemoService().getSomethingWithCacheCount());
        assert getWithCacheDemoService().getSomethingWithCacheCount() == 8;
    }

    @Test
    public void testSomethingWithCacheCloneReturn() throws Exception {
        long start = System.currentTimeMillis();
        Date date = getWithCacheDemoService().getSomethingWithCacheCloneReturn("hello");
        System.out.println(date + "," + new Date());
        date = getWithCacheDemoService().getSomethingWithCacheCloneReturn("hello");
        System.out.println(date + "," + new Date());
        date = getWithCacheDemoService().getSomethingWithCacheCloneReturn("hello");
        System.out.println(date + "," + new Date());
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) >= 3000 && (end - start) < 3800;
    }

    @Test
    public void testSomethingWithRedis() throws Exception {
        long start = System.currentTimeMillis();
        List<Date> dates = getWithCacheDemoService().getSomethingWithRedis();
        System.out.println(dates + "," + new Date());
        dates = getWithCacheDemoService().getSomethingWithRedis();
        System.out.println(dates + "," + new Date());
        dates = getWithCacheDemoService().getSomethingWithRedis();
        assert dates.get(0) != null && dates.get(0) instanceof Date;
        System.out.println(dates + "," + new Date());
        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) > 3000 && (end - start) < 4500; // 不要超过6秒，就是合理的误差范围内
    }

    @Test
    public void testLittleBenchmark() throws Exception {
        System.out.println("start at " + new Date());

        getWithCacheDemoService().getSomethingWithCache();
        getWithCacheDemoService().resetSomethingWithCacheCount();

        int times = 10000000;
        // 测试调用1000万次的时间
        long start = System.currentTimeMillis();
        for(int i = 0; i < times; i++) {
            getWithCacheDemoService().getSomethingWithCache();
        }
        long end = System.currentTimeMillis();

        System.out.println("end at " + new Date());

        long cost = (end - start);
        System.out.println("cost:" + cost + "ms");
        double qps = times / (cost / 1000.0);
        System.out.println("qps:" + qps);

        System.out.println("call count:" + getWithCacheDemoService().getSomethingWithCacheCount());
        assert qps > 100000;  // qps应该至少10万以上，正常都有100万
        assert getWithCacheDemoService().getSomethingWithCacheCount() <= ((int)cost/1000);

        HiSpeedCacheStatisticDTO statistic = HiSpeedCacheContext.getStatistic();
        assert statistic.getCacheDataCount() >= 1;
    }

    @Test
    public void testBenchmarkRedisCache() throws Exception {
        // 测试调用1000万次的时间
        int times = 10000000;
        long start = System.currentTimeMillis();
        for(int i = 0; i < times; i++) {
            getWithCacheDemoService().getSomethingWithRedis();
        }
        long end = System.currentTimeMillis();

        long cost = (end - start);
        System.out.println("cost:" + cost + "ms");
        double qps = times / (cost / 1000.0);
        System.out.println("qps:" + qps);

        assert qps > 200000;  // qps应该20万以上，如果不用cacheRedisDataMillisecond是不可能达到20万qps的
    }

    @Test
    public void testExpireTime() throws Exception {
        UUID.randomUUID(); // 第一次跑这个比较久，所以先预热

        // 这里大概运行20+秒，除了头和尾，大概应该有18次是cost在1000到1002之间，误差不会超过1毫秒（除了四舍五入）
        List<Long> costList = new ArrayList<>();

        long lastGetTime = System.currentTimeMillis();
        String lastUuid = getWithCacheDemoService().getRandomString();
        for (int i = 0; i < 10000; i++) {
            String uuid = getWithCacheDemoService().getRandomString();
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

            List<Date> dates = getWithCacheDemoService().getSomeDateWithCache();
            System.out.println(dates.get(0).getClass());
            System.out.println(JSON.toJson(dates));

            dates.forEach(o -> {
                assert o.getClass().equals(Date.class);
            });

            Thread.sleep(1000);
        }

        for (int i = 0; i < 10; i++) {
            Map<String, Date> dates = getWithCacheDemoService().getSomeDateWithCache2();
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
        getWithCacheDemoService().withParam("hi");
    }

    @Test
    public void testCacheCondition() throws Exception {

        long start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            getWithCacheDemoService().getSomethingWithCacheCondition("pugwoo");
        }
        long end = System.currentTimeMillis();
        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) < 1200; // 走了缓存

        start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            getWithCacheDemoService().getSomethingWithCacheCondition("hello"); // 这个参数走不到索引
        }
        end = System.currentTimeMillis();
        System.out.println("cost:" + (end - start) + "ms");
        assert (end - start) > 5000; // 没走缓存
    }

    @Test
    public void testHiSpeedContext() throws Exception {
        // 先调用一下，触发缓存
        getWithCacheDemoService().getSomethingWithException(false);

        // 调用1次，走缓存
        long start = System.currentTimeMillis();
        getWithCacheDemoService().getSomethingWithException(false);
        long end = System.currentTimeMillis();
        assert (end - start) < 100;

        // 禁用缓存，此时不走缓存
        start = System.currentTimeMillis();
        HiSpeedCacheContext.disableOnce();
        getWithCacheDemoService().getSomethingWithException(false);
        end = System.currentTimeMillis();
        assert (end - start) >= 3000;

        // 再次调用，走缓存
        start = System.currentTimeMillis();
        getWithCacheDemoService().getSomethingWithException(false);
        end = System.currentTimeMillis();
        assert (end - start) < 100;

        // 强制刷新
        start = System.currentTimeMillis();
        HiSpeedCacheContext.forceRefreshOnce();
        getWithCacheDemoService().getSomethingWithException(false);
        end = System.currentTimeMillis();
        assert (end - start) >= 3000;

        // 再次调用，走缓存
        start = System.currentTimeMillis();
        getWithCacheDemoService().getSomethingWithException(false);
        end = System.currentTimeMillis();
        assert (end - start) < 100;

        // 尝试强制刷新，成功的情况
        start = System.currentTimeMillis();
        HiSpeedCacheContext.tryForceRefreshOnce();
        getWithCacheDemoService().getSomethingWithException(false);
        end = System.currentTimeMillis();
        assert (end - start) >= 3000;

        // 尝试强制刷新，失败的情况
        start = System.currentTimeMillis();
        HiSpeedCacheContext.tryForceRefreshOnce();
        String result = getWithCacheDemoService().getSomethingWithException(true); // 故意抛异常
        end = System.currentTimeMillis();
        assert (end - start) < 100; // 此时能走缓存
        assert "ok".equals(result); // 结果也正确
        System.out.println("cost:" + (end - start) + "ms");
    }

    /**
     * 测试 cacheRebuildWaitMs > 0 的情况
     * 当多个请求同时进来时，follower线程等待leader线程的结果
     */
    @Test
    public void testCacheRebuildWaitMs_WithWait() throws Exception {
        Thread.sleep(15000); // 等待之前的缓存过期
        getWithCacheDemoService().resetCacheRebuildWaitMsCallCount();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        // 启动多个线程同时请求
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程准备好
                    String result = getWithCacheDemoService().getSlowDataWithWait();
                    results.add(result);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown(); // 同时启动所有线程
        endLatch.await(5, TimeUnit.SECONDS); // 等待所有线程完成
        long end = System.currentTimeMillis();

        executor.shutdown();

        System.out.println("testCacheRebuildWaitMs_WithWait cost:" + (end - start) + "ms");
        System.out.println("testCacheRebuildWaitMs_WithWait call count:" + getWithCacheDemoService().getCacheRebuildWaitMsCallCount());
        System.out.println("testCacheRebuildWaitMs_WithWait success count:" + successCount.get());

        // 验证：所有线程都成功获取到结果
        assert successCount.get() == threadCount;
        // 验证：由于follower等待leader的结果，实际调用次数应该是1次（只有leader调用）
        assert getWithCacheDemoService().getCacheRebuildWaitMsCallCount() == 1;
        // 验证：所有结果应该相同（都是leader的结果）
        String firstResult = results.get(0);
        for (String result : results) {
            assert result.equals(firstResult);
        }
        // 验证：总耗时应该接近1秒（leader的执行时间），而不是10秒（10个线程各执行1秒）
        assert (end - start) >= 1000 && (end - start) < 2000;
    }

    /**
     * 测试 cacheRebuildWaitMs = 0 的情况
     * 当多个请求同时进来时，follower线程不等待，直接调用业务方法
     */
    @Test
    public void testCacheRebuildWaitMs_WithoutWait() throws Exception {
        Thread.sleep(15000); // 等待之前的缓存过期
        getWithCacheDemoService().resetCacheRebuildWaitMsCallCount();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 启动多个线程同时请求
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程准备好
                    getWithCacheDemoService().getSlowDataWithoutWait();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown(); // 同时启动所有线程
        endLatch.await(15, TimeUnit.SECONDS); // 等待所有线程完成
        long end = System.currentTimeMillis();

        executor.shutdown();

        System.out.println("testCacheRebuildWaitMs_WithoutWait cost:" + (end - start) + "ms");
        System.out.println("testCacheRebuildWaitMs_WithoutWait call count:" + getWithCacheDemoService().getCacheRebuildWaitMsCallCount());
        System.out.println("testCacheRebuildWaitMs_WithoutWait success count:" + successCount.get());

        // 验证：所有线程都成功获取到结果
        assert successCount.get() == threadCount;
        // 验证：由于不等待，所有线程都会调用业务方法
        assert getWithCacheDemoService().getCacheRebuildWaitMsCallCount() == threadCount;
        // 验证：总耗时应该接近1秒（并发执行），而不是10秒（串行执行）
        assert (end - start) >= 1000 && (end - start) < 2000;
    }

    /**
     * 测试 cacheRebuildWaitMs 超时的情况
     * 当leader执行时间超过等待时间时，follower线程超时后自己调用业务方法
     */
    @Test
    public void testCacheRebuildWaitMs_Timeout() throws Exception {
        Thread.sleep(15000); // 等待之前的缓存过期
        getWithCacheDemoService().resetCacheRebuildWaitMsCallCount();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 启动多个线程同时请求
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程准备好
                    getWithCacheDemoService().getVerySlowDataWithShortWait();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown(); // 同时启动所有线程
        endLatch.await(15, TimeUnit.SECONDS); // 等待所有线程完成
        long end = System.currentTimeMillis();

        executor.shutdown();

        System.out.println("testCacheRebuildWaitMs_Timeout cost:" + (end - start) + "ms");
        System.out.println("testCacheRebuildWaitMs_Timeout call count:" + getWithCacheDemoService().getCacheRebuildWaitMsCallCount());
        System.out.println("testCacheRebuildWaitMs_Timeout success count:" + successCount.get());

        // 验证：所有线程都成功获取到结果
        assert successCount.get() == threadCount;
        // 验证：由于等待超时（500ms < 2000ms执行时间），follower线程会自己调用业务方法
        // 所以调用次数应该大于1（leader + 部分或全部follower）
        assert getWithCacheDemoService().getCacheRebuildWaitMsCallCount() > 1;
        // 验证：总耗时应该接近2秒（leader执行时间），因为follower超时后也会并发执行
        assert (end - start) >= 2000 && (end - start) < 3000;
    }

    /**
     * 测试 leader 调用失败的情况
     * 当leader调用失败时，follower线程应该自己调用业务方法
     */
    @Test
    public void testCacheRebuildWaitMs_LeaderFailure() throws Exception {
        Thread.sleep(15000); // 等待之前的缓存过期
        getWithCacheDemoService().resetCacheRebuildWaitMsCallCount();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger firstCall = new AtomicInteger(0);

        // 启动多个线程同时请求
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程准备好
                    // 第一个线程（leader）会失败，其他线程应该成功
                    boolean shouldFail = firstCall.getAndIncrement() == 0;
                    String result = getWithCacheDemoService().getDataWithLeaderFailure(shouldFail);
                    System.out.println(shouldFail + "=========" + result);
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // leader失败是预期的
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown(); // 同时启动所有线程
        endLatch.await(10, TimeUnit.SECONDS); // 等待所有线程完成
        long end = System.currentTimeMillis();

        executor.shutdown();

        System.out.println("testCacheRebuildWaitMs_LeaderFailure cost:" + (end - start) + "ms");
        System.out.println("testCacheRebuildWaitMs_LeaderFailure call count:" + getWithCacheDemoService().getCacheRebuildWaitMsCallCount());
        System.out.println("testCacheRebuildWaitMs_LeaderFailure success count:" + successCount.get());

        // 验证：除了leader失败外，其他线程都应该成功
        assert successCount.get() == threadCount - 1;
        // 验证：由于leader失败，follower线程会自己调用业务方法
        // 所以调用次数应该是threadCount（leader失败 + 所有follower重新调用）
        assert getWithCacheDemoService().getCacheRebuildWaitMsCallCount() == threadCount;
        // 验证：总耗时应该接近500ms（单次执行时间），因为follower会并发执行
        assert (end - start) >= 500 && (end - start) < 1500;
    }

}
