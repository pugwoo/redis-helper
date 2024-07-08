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
        // 起10个线程，连续调用3分20秒，非收尾时间段肯定是完整的1分钟，此时调用成功的数量应该是20000个
        long endTime = System.currentTimeMillis() + 3 * 60 * 1000 + 20 * 1000;
        List<Thread> threads = new ArrayList<>();
        Map<String, AtomicLong> timeCount = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                while (System.currentTimeMillis() <= endTime) {
                    try {
                        int second = DateUtils.getSecond(new Date());
                        if (second < 10 || second > 50) { // 为了避免本地和Redis的时间差，这里只在10-50秒之间调用
                            continue;
                        }

                        String uuid = UUID.randomUUID().toString();
                        String uuid2 = getRateLimitService().limitPerMinute(uuid);
                        assert uuid.equals(uuid2);

                        synchronized (timeCount) {
                            String minute = DateUtils.format(new Date(), "yyyy-MM-dd HH:mm");
                            AtomicLong atomicLong = timeCount.get(minute);
                            if (atomicLong == null) {
                                atomicLong = new AtomicLong();
                                timeCount.put(minute, atomicLong);
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

        // 不是开始和结束的时间段，应该是20000个
        List<Map<String, Object>> list = ListUtils.transform(timeCount.entrySet(), o -> MapUtils.of("key", o.getKey(), "value", o.getValue()));
        ListUtils.sortAscNullLast(list, o -> (String) o.get("key"));
        boolean atLeastCheckOne = false;
        for (int i = 1; i < list.size() - 1; i++) {
            assert list.get(i).get("value").toString().equals(String.valueOf(20000L));
            atLeastCheckOne = true;
        }
        assert atLeastCheckOne;
    }


    @Test
    public void testRateLimit2() {
        // 起10个线程，连续调用3分20秒，非收尾时间段肯定是完整的1分钟，此时调用成功的数量应该是20000个
        long endTime = System.currentTimeMillis() + 3 * 60 * 1000 + 20 * 1000;
        List<Thread> threads = new ArrayList<>();
        Map<String, AtomicLong> timeCount = new HashMap<>();
        Map<String, AtomicLong> timeCount2 = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                while (System.currentTimeMillis() <= endTime) {
                    try {
                        int second = DateUtils.getSecond(new Date());
                        if (second < 10 || second > 50) { // 为了避免本地和Redis的时间差，这里只在10-50秒之间调用
                            continue;
                        }
                        if (second % 10 < 2 || second % 10 > 8) { // 为了避免本地和Redis的时间差，这里只在2~8秒之间调用
                            continue;
                        }

                        String uuid = UUID.randomUUID().toString();
                        String uuid2 = getRateLimitService().limitPerMinute2(uuid);
                        assert uuid.equals(uuid2);

                        synchronized (timeCount) {
                            String minute = DateUtils.format(new Date(), "yyyy-MM-dd HH:mm");
                            AtomicLong atomicLong = timeCount.get(minute);
                            if (atomicLong == null) {
                                atomicLong = new AtomicLong();
                                timeCount.put(minute, atomicLong);
                            }
                            atomicLong.incrementAndGet();

                            String seconds = DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
                            seconds = seconds.substring(0, seconds.length() - 1) + "0";
                            atomicLong = timeCount2.get(seconds);
                            if (atomicLong == null) {
                                atomicLong = new AtomicLong();
                                timeCount2.put(seconds, atomicLong);
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

            // 不是开始和结束的时间段，应该是20000个
            ListUtils.sortAscNullLast(list, o -> (String) o.get("key"));
            boolean atLeastCheckOne = false;
            for (int i = 1; i < list.size() - 1; i++) {
                assert list.get(i).get("value").toString().equals(String.valueOf(1000L));
                atLeastCheckOne = true;
            }
            assert atLeastCheckOne;
        }

        {
            List<Map<String, Object>> list = ListUtils.transform(timeCount2.entrySet(), o -> MapUtils.of("key", o.getKey(), "value", o.getValue()));
            ListUtils.sortAscNullLast(list, o -> (String) o.get("key"));
            System.out.println(JSON.toJson(list));

            // 中间2分钟都是1000个，因此至少会出现4个400和2个200
            int count400 = 0;
            int count200 = 0;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).get("value").toString().equals(String.valueOf(400L))) {
                    count400++;
                } else if (list.get(i).get("value").toString().equals(String.valueOf(200L))) {
                    count200++;
                }
            }
            assert count400 >= 4;
            assert count200 >= 2;
        }

    }

}
