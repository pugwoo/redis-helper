package com.pugwoo.redishelpertest.common;

import com.pugwoo.redishelpertest.ratelimit.RateLimitService;
import com.pugwoo.wooutils.collect.ListUtils;
import com.pugwoo.wooutils.collect.MapUtils;
import com.pugwoo.wooutils.json.JSON;
import com.pugwoo.wooutils.lang.DateUtils;
import com.pugwoo.wooutils.redis.exception.ExceedRateLimitException;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TestRedisRateLimit {

    public abstract RateLimitService getRateLimitService();

    @Test
    public void testRateLimit() {
        // 起10个线程，连续调用35秒，非收尾时间段肯定是完整的10秒，此时调用成功的数量应该是1000个
        long endTime = System.currentTimeMillis() + 35 * 1000;
        List<Thread> threads = new ArrayList<>();
        Map<String, AtomicLong> timeCount = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                while (System.currentTimeMillis() <= endTime) {
                    try {
                        int second = DateUtils.getSecond(new Date());
                        int secondMod10 = second % 10;
                        if (secondMod10 < 2 || secondMod10 > 8) { // 为了避免本地和Redis的时间差，这里只在每10秒的2-8秒之间调用
                            continue;
                        }

                        String uuid = UUID.randomUUID().toString();
                        String uuid2 = getRateLimitService().limitPerMinute(uuid);
                        assert uuid.equals(uuid2);

                        synchronized (timeCount) {
                            String tenSecond = DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
                            tenSecond = tenSecond.substring(0, tenSecond.length() - 1) + "0";
                            AtomicLong atomicLong = timeCount.get(tenSecond);
                            if (atomicLong == null) {
                                atomicLong = new AtomicLong();
                                timeCount.put(tenSecond, atomicLong);
                            }
                            atomicLong.incrementAndGet();
                        }
                    } catch (ExceedRateLimitException e) {
                        // ignored
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }

        // 等待线程结束
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // ignored
            }
        }

        System.out.println(JSON.toJson(timeCount));

        // 不是开始和结束的时间段，应该是1000个
        List<Map<String, Object>> list = ListUtils.transform(timeCount.entrySet(), o -> MapUtils.of("key", o.getKey(), "value", o.getValue()));
        ListUtils.sortAscNullLast(list, o -> (String) o.get("key"));
        boolean atLeastCheckOne = false;
        for (int i = 1; i < list.size() - 1; i++) {
            assert list.get(i).get("value").toString().equals(String.valueOf(1000L));
            atLeastCheckOne = true;
        }
        assert atLeastCheckOne;
    }


    @Test
    public void testRateLimit2() {
        // 起10个线程，连续调用35秒，非收尾时间段肯定是完整的10秒，此时调用成功的数量应该是100个
        long endTime = System.currentTimeMillis() + 35 * 1000;
        List<Thread> threads = new ArrayList<>();
        Map<String, AtomicLong> timeCount = new HashMap<>();
        Map<String, AtomicLong> timeCount2 = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                while (System.currentTimeMillis() <= endTime) {
                    try {
                        int second = DateUtils.getSecond(new Date());
                        int secondMod10 = second % 10;
                        if (secondMod10 < 2 || secondMod10 > 8) { // 为了避免本地和Redis的时间差，这里只在每10秒的2-8秒之间调用
                            continue;
                        }

                        String uuid = UUID.randomUUID().toString();
                        String uuid2 = getRateLimitService().limitPerMinute2(uuid);
                        assert uuid.equals(uuid2);

                        synchronized (timeCount) {
                            String tenSecond = DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
                            tenSecond = tenSecond.substring(0, tenSecond.length() - 1) + "0";
                            AtomicLong atomicLong = timeCount.get(tenSecond);
                            if (atomicLong == null) {
                                atomicLong = new AtomicLong();
                                timeCount.put(tenSecond, atomicLong);
                            }
                            atomicLong.incrementAndGet();

                            String oneSecond = DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
                            atomicLong = timeCount2.get(oneSecond);
                            if (atomicLong == null) {
                                atomicLong = new AtomicLong();
                                timeCount2.put(oneSecond, atomicLong);
                            }
                            atomicLong.incrementAndGet();
                        }
                    } catch (ExceedRateLimitException e) {
                        // ignored
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }

        // 等待线程结束
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // ignored
            }
        }

        {
            List<Map<String, Object>> list = ListUtils.transform(timeCount.entrySet(), o -> MapUtils.of("key", o.getKey(), "value", o.getValue()));
            ListUtils.sortAscNullLast(list, o -> (String) o.get("key"));
            System.out.println(JSON.toJson(list));

            // 不是开始和结束的时间段，应该是100个
            ListUtils.sortAscNullLast(list, o -> (String) o.get("key"));
            boolean atLeastCheckOne = false;
            for (int i = 1; i < list.size() - 1; i++) {
                assert list.get(i).get("value").toString().equals(String.valueOf(100L));
                atLeastCheckOne = true;
            }
            assert atLeastCheckOne;
        }

        {
            List<Map<String, Object>> list = ListUtils.transform(timeCount2.entrySet(), o -> MapUtils.of("key", o.getKey(), "value", o.getValue()));
            ListUtils.sortAscNullLast(list, o -> (String) o.get("key"));
            System.out.println(JSON.toJson(list));

            // 中间20秒都是100个，因此至少会出现4个40和2个20
            int count40 = 0;
            int count20 = 0;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).get("value").toString().equals(String.valueOf(40L))) {
                    count40++;
                } else if (list.get(i).get("value").toString().equals(String.valueOf(20L))) {
                    count20++;
                }
            }
            assert count40 >= 4;
            assert count20 >= 2;
        }

    }

}
