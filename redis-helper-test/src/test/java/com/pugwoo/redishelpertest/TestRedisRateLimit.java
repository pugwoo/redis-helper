package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.ratelimit.RateLimitService;
import com.pugwoo.wooutils.collect.ListUtils;
import com.pugwoo.wooutils.collect.MapUtils;
import com.pugwoo.wooutils.json.JSON;
import com.pugwoo.wooutils.lang.DateUtils;
import com.pugwoo.wooutils.redis.exception.ExceedRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest
public class TestRedisRateLimit {

    @Autowired
    private RateLimitService rateLimitService;

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
                        String uuid2 = rateLimitService.limitPerMinute(uuid);
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
        for (int i = 1; i < list.size() - 1; i++) {
            assert list.get(i).get("value").toString().equals(String.valueOf(20000L));
        }
    }

}
