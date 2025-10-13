package com.pugwoo.redishelpertest.common;

import com.pugwoo.redishelpertest.redis.sync.LockModeTestService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试锁模式（排它锁和共享锁）
 */
public abstract class TestLockMode {

    public abstract LockModeTestService getLockModeTestService();

    /**
     * 测试排它锁：多个线程竞争，只有一个能执行
     */
    @Test
    public void testExclusiveLock() throws Exception {
        System.out.println("\n========== 测试排它锁 ==========");
        List<Thread> threads = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        long start = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            final int threadNum = i;
            Thread t = new Thread(() -> {
                try {
                    String result = getLockModeTestService().exclusiveLockMethod("thread-" + threadNum);
                    successCount.incrementAndGet();
                    System.out.println("线程 " + threadNum + " 执行成功，结果: " + result);
                } catch (Exception e) {
                    System.out.println("线程 " + threadNum + " 执行失败: " + e.getMessage());
                }
            });
            t.start();
            threads.add(t);
        }

        for (Thread t : threads) {
            t.join();
        }

        long cost = System.currentTimeMillis() - start;
        System.out.println("排它锁测试完成，成功执行: " + successCount.get() + " 次，总耗时: " + cost + "ms");
        
        // 排它锁应该串行执行，所以总时间应该大于等于 2000ms * 成功次数
        assert cost >= 2000 * successCount.get() - 500;
    }

    /**
     * 测试共享锁：多个线程可以同时执行
     */
    @Test
    public void testShareLock() throws Exception {
        System.out.println("\n========== 测试共享锁 ==========");
        List<Thread> threads = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        long start = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            final int threadNum = i;
            Thread t = new Thread(() -> {
                try {
                    String result = getLockModeTestService().shareLockMethod("thread-" + threadNum);
                    successCount.incrementAndGet();
                    System.out.println("线程 " + threadNum + " 执行成功，结果: " + result);
                } catch (Exception e) {
                    System.out.println("线程 " + threadNum + " 执行失败: " + e.getMessage());
                }
            });
            t.start();
            threads.add(t);
        }

        for (Thread t : threads) {
            t.join();
        }

        long cost = System.currentTimeMillis() - start;
        System.out.println("共享锁测试完成，成功执行: " + successCount.get() + " 次，总耗时: " + cost + "ms");
        
        // 共享锁应该并行执行，所以总时间应该接近单次执行时间
        assert successCount.get() == 3;
        assert cost < 4000; // 应该远小于串行执行的 6000ms
    }

    /**
     * 测试读写锁场景：写操作用排它锁，读操作用共享锁
     */
    @Test
    public void testReadWriteLock() throws Exception {
        System.out.println("\n========== 测试读写锁 ==========");
        List<Thread> threads = new ArrayList<>();
        long start = System.currentTimeMillis();

        // 启动一个写线程
        Thread writeThread = new Thread(() -> {
            try {
                getLockModeTestService().writeData("test-data");
                System.out.println("写线程执行完成");
            } catch (Exception e) {
                System.out.println("写线程执行失败: " + e.getMessage());
            }
        });
        writeThread.start();
        threads.add(writeThread);

        // 稍微延迟，确保写线程先获取锁
        Thread.sleep(100);

        // 启动多个读线程
        for (int i = 0; i < 3; i++) {
            final int threadNum = i;
            Thread readThread = new Thread(() -> {
                try {
                    String result = getLockModeTestService().readData();
                    System.out.println("读线程 " + threadNum + " 执行完成，结果: " + result);
                } catch (Exception e) {
                    System.out.println("读线程 " + threadNum + " 执行失败: " + e.getMessage());
                }
            });
            readThread.start();
            threads.add(readThread);
        }

        for (Thread t : threads) {
            t.join();
        }

        long cost = System.currentTimeMillis() - start;
        System.out.println("读写锁测试完成，总耗时: " + cost + "ms");
        
        // 写操作完成后，读操作应该能并行执行
        assert cost >= 1000; // 至少写操作的时间
        assert cost < 3000; // 读操作应该并行，不会太长
    }

    /**
     * 测试共享锁带心跳机制
     */
    @Test
    public void testShareLockWithHeartbeat() throws Exception {
        System.out.println("\n========== 测试共享锁带心跳 ==========");
        List<Thread> threads = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final int threadNum = i;
            Thread t = new Thread(() -> {
                try {
                    String result = getLockModeTestService().shareLockWithHeartbeat("thread-" + threadNum);
                    successCount.incrementAndGet();
                    System.out.println("线程 " + threadNum + " 执行成功，结果: " + result);
                } catch (Exception e) {
                    System.out.println("线程 " + threadNum + " 执行失败: " + e.getMessage());
                }
            });
            t.start();
            threads.add(t);
        }

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("共享锁心跳测试完成，成功执行: " + successCount.get() + " 次");
        assert successCount.get() == 2;
    }

    /**
     * 测试共享锁带keyScript
     */
    @Test
    public void testShareLockWithKey() throws Exception {
        System.out.println("\n========== 测试共享锁带KeyScript ==========");
        List<Thread> threads = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        // 使用相同的key，应该可以并行执行（共享锁）
        for (int i = 0; i < 3; i++) {
            final int threadNum = i;
            Thread t = new Thread(() -> {
                try {
                    String result = getLockModeTestService().shareLockWithKey("key1", "value-" + threadNum);
                    successCount.incrementAndGet();
                    System.out.println("线程 " + threadNum + " 执行成功，结果: " + result);
                } catch (Exception e) {
                    System.out.println("线程 " + threadNum + " 执行失败: " + e.getMessage());
                }
            });
            t.start();
            threads.add(t);
        }

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("共享锁KeyScript测试完成，成功执行: " + successCount.get() + " 次");
        assert successCount.get() == 3;
    }

    /**
     * 测试混合锁（多个锁注解）
     */
    @Test
    public void testMixedLock() throws Exception {
        System.out.println("\n========== 测试混合锁 ==========");
        List<Thread> threads = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final int threadNum = i;
            Thread t = new Thread(() -> {
                try {
                    String result = getLockModeTestService().mixedLockMethod("thread-" + threadNum);
                    successCount.incrementAndGet();
                    System.out.println("线程 " + threadNum + " 执行成功，结果: " + result);
                } catch (Exception e) {
                    System.out.println("线程 " + threadNum + " 执行失败: " + e.getMessage());
                }
            });
            t.start();
            threads.add(t);
        }

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("混合锁测试完成，成功执行: " + successCount.get() + " 次");
        // 混合锁需要同时获取两个锁，应该串行执行
        assert successCount.get() >= 1;
    }
}

