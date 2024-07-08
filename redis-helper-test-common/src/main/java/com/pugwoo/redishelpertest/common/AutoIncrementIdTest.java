package com.pugwoo.redishelpertest.common;

import com.pugwoo.wooutils.redis.RedisHelper;
import org.junit.jupiter.api.Test;

import java.util.*;

public abstract class AutoIncrementIdTest {

    public abstract RedisHelper getRedisHelper();

    @Test
    public void test() throws Exception {
        final List<Long> ids = new Vector<>();

        long start = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();

        int THREAD = 20;
        int TIMES = 2000;

        String namespace = "ORDER" + UUID.randomUUID();

        // redis最大连接数一般到900，受服务器打开文件数限制，需要修改系统配置: 最大连接数
        for(int t = 0; t < THREAD; t++) {
            Thread thread = new Thread(() -> {
                for(int i = 0; i < TIMES; i++) {
                    Long id = getRedisHelper().getAutoIncrementId(namespace, 120);
                    //       System.out.println(id);
                    if (id != null) { // null可能是网络原因导致的，不加入
                        ids.add(id);
                    }
                }
            });
            thread.start();
            threads.add(thread);
        }

        for(Thread thread : threads) {
            thread.join();
        }

        long end = System.currentTimeMillis();
        System.out.println("cost:" + (end - start) + "ms");

        // 校验正确性
        Set<Long> set = new HashSet<>();
        boolean isDup = false;
        for(Long id : ids) {
            if(set.contains(id)) {
                isDup = true;
            } else {
                set.add(id);
            }
        }
        System.out.println(isDup ? "数据错误，有重复" : "数据正确");

        System.out.println(ids.size());

        assert !isDup;
        assert ids.size() == THREAD * TIMES;
        System.out.println("total size:" + ids.size());
    }

    @Test
    public void testBatch() throws Exception {
        final List<Long> ids = new Vector<>();

        long start = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();

        int THREAD = 20;
        int TIMES = 20;
        int BATCH_NUM = 100;

        String namespace = "ORDER" + UUID.randomUUID();

        // redis最大连接数一般到900，受服务器打开文件数限制，需要修改系统配置: 最大连接数
        for(int t = 0; t < THREAD; t++) {
            Thread thread = new Thread(() -> {
                for(int i = 0; i < TIMES; i++) {
                    List<Long> idList = getRedisHelper().getAutoIncrementIdBatch(namespace, BATCH_NUM, 120);
                    if (idList != null) { // null可能是网络原因导致的，不加入
                        ids.addAll(idList);
                    }
                }
            });
            thread.start();
            threads.add(thread);
        }

        for(Thread thread : threads) {
            thread.join();
        }

        long end = System.currentTimeMillis();
        System.out.println("cost:" + (end - start) + "ms");

        // 校验正确性
        Set<Long> set = new HashSet<>();
        boolean isDup = false;
        for(Long id : ids) {
            if(set.contains(id)) {
                isDup = true;
            } else {
                set.add(id);
            }
        }
        System.out.println(isDup ? "数据错误，有重复" : "数据正确");

        System.out.println(ids.size());

        assert !isDup;
        assert ids.size() == THREAD * TIMES * BATCH_NUM;
        System.out.println("total size:" + ids.size());
    }

}
