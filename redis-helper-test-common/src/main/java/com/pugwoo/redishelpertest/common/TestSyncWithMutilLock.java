package com.pugwoo.redishelpertest.common;

import com.pugwoo.redishelpertest.redis.sync.HelloServiceWithMutilLock;
import com.pugwoo.wooutils.redis.RedisSyncContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public abstract class TestSyncWithMutilLock {

    public abstract HelloServiceWithMutilLock getHelloServiceWithMutilLock();

    @Test
    public void testAdd() throws Exception {

        System.out.println("a: " + getHelloServiceWithMutilLock().getA());

        List<Thread> thread = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            Thread t = new Thread(() -> {
                try {
                    for (int i1 = 0; i1 < 30; i1++) {
                        getHelloServiceWithMutilLock().add();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            t.start();
            thread.add(t);
        }
        for (Thread t : thread) {
            t.join();
        }
        System.out.println("a: " + getHelloServiceWithMutilLock().getA());

        RedisSyncContext.printCostInfo();

        assert getHelloServiceWithMutilLock().getA() == 30 * 10;
    }

    @Test
    public void testHello() throws Exception {
        List<Thread> thread = new ArrayList<>();

        long start = System.currentTimeMillis();

        for (int i = 1; i <= 10; i++) {
            final int a = i;
            Thread t = new Thread(() -> {
                try {
                    while (true) {
                        getHelloServiceWithMutilLock().hello("nick", a);
                        System.out.println("线程" + a +
                                "执行结果详情: 是否执行了方法:" + RedisSyncContext.getHaveRun());
                        if (RedisSyncContext.getHaveRun()) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            t.start();
            thread.add(t);
        }

        for (Thread t : thread) {
            t.join();
        }

        long end = System.currentTimeMillis();
        assert end - start >= 10000;
        assert end - start <= 19000;

    }

    @Test
    public void testHelloByDefaultNamespace() throws Exception {
        List<Thread> thread = new ArrayList<>();

        long start = System.currentTimeMillis();

        for (int i = 1; i <= 10; i++) {
            final int a = i;
            Thread t = new Thread(() -> {
                try {
                    do {
                        getHelloServiceWithMutilLock().helloByDefaultNamespace("nick", a);
                        System.out.println("线程" + a +
                                "执行结果详情: 是否执行了方法:" + RedisSyncContext.getHaveRun());
                    } while (!RedisSyncContext.getHaveRun());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            t.start();
            thread.add(t);
        }

        for (Thread t : thread) {
            t.join();
        }

        long end = System.currentTimeMillis();
        assert end - start >= 10000;
        assert end - start <= 19000;

    }

}
