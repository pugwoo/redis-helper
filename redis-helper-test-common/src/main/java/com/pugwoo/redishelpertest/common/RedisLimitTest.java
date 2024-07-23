package com.pugwoo.redishelpertest.common;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.RedisLimitParam;
import com.pugwoo.wooutils.redis.RedisLimitPeriodEnum;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public abstract class RedisLimitTest {

    public abstract RedisHelper getRedisHelper();

    @Test
    public void test() throws Exception {
        final int LIMIT = 1000;
        final int THREAD = 100;

        // 一个redisLimitParam 相当于是一个业务配置，例如每分钟只能请求1000次
        final RedisLimitParam redisLimitParam = new RedisLimitParam();
        redisLimitParam.setNamespace("VIEW-LIMIT" + UUID.randomUUID().toString()); // 每个业务单独设置，每个业务不同
        redisLimitParam.setLimitPeriod(RedisLimitPeriodEnum.HOUR);
        redisLimitParam.setLimitCount(LIMIT);

        final Set<Long> vector = new ConcurrentSkipListSet<>();
        List<Thread> threads = new Vector<Thread>();
        for(int i = 0; i < THREAD; i++) { // 模拟100个线程
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    long count = 0;
                    do {
                        count = getRedisHelper().useLimitCount(redisLimitParam, "192.168.2.3");
                        if(count > 0) {
                            System.out.println(Thread.currentThread().getName() +
                                    "抢到了第" + count + "个，时间:" + new Date());
                            vector.add(count);
                            try {
                                Thread.sleep(2); // 抢到了等2毫秒再抢
                            } catch (InterruptedException e) {
                            }
                        } else {
                            System.out.println("抢完了，线程" + Thread.currentThread().getName()
                                    + "退出");
                            break;
                        }
                    } while(true);
                }
            }, "线程"+i);
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
        }

        for(Thread thread : threads) {
            thread.join();
        }
        System.out.println("final:" + vector.size());

        assert vector.size() == LIMIT;

        // 检查一下vector里面的数据最大值是LIMIT，最小是1
        Long max = null;
        Long min = null;

        for (Long v : vector) {
            if (max == null || v > max) {
                max = v;
            }
            if (min == null || v < min) {
                min = v;
            }
        }

        assert max == LIMIT;
        assert min == 1;

    }

}
