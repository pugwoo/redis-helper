package com.pugwoo.redishelpertest.common;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.string.StringTools;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class LockTest {

    public abstract RedisHelper getRedisHelper();

    private static final String namespace = "testReentrant";

    @Test
    public void testReentrant() throws Exception {
        // 1. 测试线程拿到keyA的锁，另外一个线程拿keyB的锁，此时线程A再去拿keyB的锁，是应该拿不到的
        //    以此来验证可重入锁不会串key
        String randomUUidA = UUID.randomUUID().toString();
        String randomUUidB = UUID.randomUUID().toString();

        AtomicBoolean isAnotherThreadGotLock = new AtomicBoolean(false);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String uuid = getRedisHelper().requireLock(namespace, randomUUidB, 10, true);
                assert StringTools.isNotBlank(uuid);
                isAnotherThreadGotLock.set(true);

                // 等待1秒后再加一次锁，此时时间线是第1.05秒
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                String uuid2 = getRedisHelper().requireLock(namespace, randomUUidB, 10, true);
                assert StringTools.isNotBlank(uuid2);
                assert uuid.equals(uuid2);

                // 等待2秒后再解锁，此时时间线是第3.1秒
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                boolean succ = getRedisHelper().releaseLock(namespace, randomUUidB, uuid, true);
                assert succ;

                // 等待2秒后再解锁，此时时间线是第5.1秒
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                boolean succ2 = getRedisHelper().releaseLock(namespace, randomUUidB, uuid, true);
                assert succ2;

                // 再继续解锁，就失败了
                boolean succ3 = getRedisHelper().releaseLock(namespace, randomUUidB, uuid, true);
                assert !succ3;
            }
        });
        thread.start();

        Thread.sleep(500);
        // 此时时间线是第0.5秒，另外一个线程已经拿到了keyB的锁

        assert isAnotherThreadGotLock.get(); // 如果这里不通过，上面的sleep可以加长一些

        String uuid = getRedisHelper().requireLock(namespace, randomUUidA, 10, true);
        assert StringTools.isNotBlank(uuid);

        String uuid2 = getRedisHelper().requireLock(namespace, randomUUidB, 10, true);
        assert StringTools.isBlank(uuid2);

        // 再加一次randomUUidA，可以拿得到
        String uuid3 = getRedisHelper().requireLock(namespace, randomUUidA, 10, true);
        assert StringTools.isNotBlank(uuid3);
        assert uuid.equals(uuid3);

        // 如果以不可重入的方式再拿randomUUidA，是拿不到的
        String uuid4 = getRedisHelper().requireLock(namespace, randomUUidA, 10, false);
        assert StringTools.isBlank(uuid4);

        // 等待1秒之后，此时时间线是第1.5秒，randomUUidB的锁还没有释放，因此还加不了锁
        Thread.sleep(1000);
        String uuid5 = getRedisHelper().requireLock(namespace, randomUUidB, 10, true);
        assert StringTools.isBlank(uuid5);

        // 等待2秒之后，此时时间线是第3.5秒，randomUUidB的锁还没有释放，因此还加不了锁
        Thread.sleep(2000);
        String uuid6 = getRedisHelper().requireLock(namespace, randomUUidB, 10, true);
        assert StringTools.isBlank(uuid6);

        // 等待2秒之后，此时时间线是第5.5秒，randomUUidB的锁已经释放，因此可以加锁
        Thread.sleep(2000);
        String uuid7 = getRedisHelper().requireLock(namespace, randomUUidB, 10, true);
        assert StringTools.isNotBlank(uuid7);
    }

    @Test
    public void testNotReentrant() throws Exception {
        String randomUUidA = UUID.randomUUID().toString();
        String uuid = getRedisHelper().requireLock(namespace, randomUUidA, 10, false);
        assert StringTools.isNotBlank(uuid);

        // 不可重入时，再拿一次，是拿不到的
        String uuid2 = getRedisHelper().requireLock(namespace, randomUUidA, 10, false);
        assert StringTools.isBlank(uuid2);
    }

    @Test
    public void test() throws Exception {
        final String nameSpace = "myname";
        final String key = "key" + UUID.randomUUID();

        final int THREAD = 10;
        final int SLEEP = 3000;

        long start = System.currentTimeMillis();

        Set<String> gotLockThreads = new ConcurrentSkipListSet<>();
        List<Thread> threads = new ArrayList<Thread>();

        for(int i=0;i<THREAD;i++){
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");

                    // 同一时刻只有一个人可以拿到lock，返回true
                    String lockUuid = getRedisHelper().requireLock(nameSpace, key, 10, true);
                    if(lockUuid != null){
                        System.out.println(df.format(new Date()) + Thread.currentThread().getName() + "拿到锁");
                    }else{
                        System.out.println(df.format(new Date()) + Thread.currentThread().getName() + "没有拿到锁，等待....");
                    }
                    if(lockUuid == null){
                        while (lockUuid == null){
                            lockUuid = getRedisHelper().requireLock(nameSpace, key, 10, true);
                        }
                        System.out.println((df.format(new Date()) +
                                Thread.currentThread().getName() + "等待后拿到锁"+System.currentTimeMillis()));
                    }

                    gotLockThreads.add(Thread.currentThread().getName());

                    try {
                        Thread.sleep(SLEEP);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    boolean succ = getRedisHelper().releaseLock(nameSpace,key,lockUuid, true);
                    assert succ;
                    System.out.println(df.format(new Date()) + Thread.currentThread().getName() + "释放锁,成功:" + succ);
                }
            });
            thread.start();
            threads.add(thread);
        }

        // 主线程等待结束
        for(Thread thread : threads) {
            thread.join();
        }

        long end = System.currentTimeMillis();

        System.out.println("main end, total cost:" + (end - start) + "ms");

        assert gotLockThreads.size() == THREAD;
        assert (end - start) >= THREAD * SLEEP;
        assert (end - start) <= THREAD * SLEEP + 5000; // 预留5秒的耗时
    }

    /**
     * 测试排它锁阻止共享锁
     */
    @Test
    public void testExclusiveLockBlocksShareLock() throws Exception {
        String key = "exclusiveKey" + UUID.randomUUID();

        // 1. 先获得排它锁
        String exclusiveLock = getRedisHelper().requireLock(namespace, key, 10, false);
        assert StringTools.isNotBlank(exclusiveLock);
        assert !exclusiveLock.endsWith("[share]");

        // 2. 尝试获得共享锁，应该失败
        String shareLock = getRedisHelper().requireShareLock(namespace, key, 10, false);
        assert StringTools.isBlank(shareLock);

        // 3. 释放排它锁
        boolean succ = getRedisHelper().releaseLock(namespace, key, exclusiveLock, false);
        assert succ;

        // 4. 现在可以获得共享锁
        String shareLock2 = getRedisHelper().requireShareLock(namespace, key, 10, false);
        assert StringTools.isNotBlank(shareLock2);

        // 5. 清理
        boolean succ2 = getRedisHelper().releaseLock(namespace, key, shareLock2, false);
        assert succ2;
    }

    /**
     * 测试共享锁允许多个客户端同时持有
     */
    @Test
    public void testMultipleShareLocks() throws Exception {
        String key = "shareKey" + UUID.randomUUID();

        // 1. 第一个客户端获得共享锁
        String shareLock1 = getRedisHelper().requireShareLock(namespace, key, 10, false);
        assert StringTools.isNotBlank(shareLock1);

        // 2. 第二个客户端也可以获得共享锁
        String shareLock2 = getRedisHelper().requireShareLock(namespace, key, 10, false);
        assert StringTools.isNotBlank(shareLock2);
        assert !shareLock1.equals(shareLock2); // 两个锁的uuid应该不同

        // 3. 第三个客户端也可以获得共享锁
        String shareLock3 = getRedisHelper().requireShareLock(namespace, key, 10, false);
        assert StringTools.isNotBlank(shareLock3);

        // 4. 释放第一个锁，其他锁仍然有效
        boolean succ1 = getRedisHelper().releaseLock(namespace, key, shareLock1, false);
        assert succ1;

        // 5. 第四个客户端仍然可以获得共享锁
        String shareLock4 = getRedisHelper().requireShareLock(namespace, key, 10, false);
        assert StringTools.isNotBlank(shareLock4);

        // 6. 清理所有锁
        boolean succ2 = getRedisHelper().releaseLock(namespace, key, shareLock2, false);
        assert succ2;
        boolean succ3 = getRedisHelper().releaseLock(namespace, key, shareLock3, false);
        assert succ3;
        boolean succ4 = getRedisHelper().releaseLock(namespace, key, shareLock4, false);
        assert succ4;
    }

    /**
     * 测试共享锁阻止排它锁
     */
    @Test
    public void testShareLockBlocksExclusiveLock() throws Exception {
        String key = "shareBlockKey" + UUID.randomUUID();

        // 1. 先获得共享锁
        String shareLock = getRedisHelper().requireShareLock(namespace, key, 10, false);
        assert StringTools.isNotBlank(shareLock);

        // 2. 尝试获得排它锁，应该失败
        String exclusiveLock = getRedisHelper().requireLock(namespace, key, 10, false);
        assert StringTools.isBlank(exclusiveLock);

        // 3. 释放共享锁
        boolean succ = getRedisHelper().releaseLock(namespace, key, shareLock, false);
        assert succ;

        // 4. 现在可以获得排它锁
        String exclusiveLock2 = getRedisHelper().requireLock(namespace, key, 10, false);
        assert StringTools.isNotBlank(exclusiveLock2);

        // 5. 清理
        boolean succ2 = getRedisHelper().releaseLock(namespace, key, exclusiveLock2, false);
        assert succ2;
    }

    /**
     * 测试共享锁的续期功能
     */
    @Test
    public void testShareLockRenewal() throws Exception {
        String key = "renewalShareKey" + UUID.randomUUID();

        // 1. 获得共享锁
        String shareLock = getRedisHelper().requireShareLock(namespace, key, 5, false);
        assert StringTools.isNotBlank(shareLock);

        // 2. 等待3秒
        Thread.sleep(3000);

        // 3. 续期锁
        boolean renewed = getRedisHelper().renewalLock(namespace, key, shareLock, 10);
        assert renewed;

        // 4. 再等待3秒，锁应该还在（因为续期到了10秒）
        Thread.sleep(3000);

        // 5. 释放锁应该成功
        boolean succ = getRedisHelper().releaseLock(namespace, key, shareLock, false);
        assert succ;
    }

    /**
     * 测试共享锁的可重入功能
     */
    @Test
    public void testShareLockReentrant() throws Exception {
        String key = "reentrantShareKey" + UUID.randomUUID();

        // 1. 获得共享锁（可重入）
        String shareLock1 = getRedisHelper().requireShareLock(namespace, key, 10, true);
        assert StringTools.isNotBlank(shareLock1);

        // 2. 再次获得共享锁（可重入），应该返回相同的uuid
        String shareLock2 = getRedisHelper().requireShareLock(namespace, key, 10, true);
        assert StringTools.isNotBlank(shareLock2);
        assert shareLock1.equals(shareLock2);

        // 3. 第一次释放，应该成功但锁还在
        boolean succ1 = getRedisHelper().releaseLock(namespace, key, shareLock1, true);
        assert succ1;

        // 4. 第二次释放，锁才真正释放
        boolean succ2 = getRedisHelper().releaseLock(namespace, key, shareLock2, true);
        assert succ2;

        // 5. 第三次释放，应该失败
        boolean succ3 = getRedisHelper().releaseLock(namespace, key, shareLock1, true);
        assert !succ3;
    }

    /**
     * 测试多线程并发获取共享锁
     */
    @Test
    public void testConcurrentShareLocks() throws Exception {
        final String key = "concurrentShareKey" + UUID.randomUUID();
        final int THREAD_COUNT = 5;
        final int HOLD_TIME = 2000; // 持有锁2秒

        Set<String> acquiredLocks = new ConcurrentSkipListSet<>();
        List<Thread> threads = new ArrayList<>();
        AtomicBoolean allAcquired = new AtomicBoolean(true);

        long start = System.currentTimeMillis();

        // 启动多个线程同时获取共享锁
        for (int i = 0; i < THREAD_COUNT; i++) {
            Thread thread = new Thread(() -> {
                String lockUuid = getRedisHelper().requireShareLock(namespace, key, 10, false);
                if (lockUuid != null) {
                    acquiredLocks.add(lockUuid);
                    try {
                        Thread.sleep(HOLD_TIME);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    boolean released = getRedisHelper().releaseLock(namespace, key, lockUuid, false);
                    assert released;
                } else {
                    allAcquired.set(false);
                }
            });
            thread.start();
            threads.add(thread);
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        long end = System.currentTimeMillis();

        // 验证所有线程都成功获取了锁
        assert allAcquired.get();
        assert acquiredLocks.size() == THREAD_COUNT;

        // 验证总时间应该接近HOLD_TIME（因为是并发的），而不是THREAD_COUNT * HOLD_TIME
        assert (end - start) < HOLD_TIME + 3000; // 预留3秒的误差
        System.out.println("Concurrent share locks test: " + THREAD_COUNT + " threads completed in " + (end - start) + "ms");
    }

    /**
     * 测试共享锁在最后一个客户端释放后，锁被完全删除
     */
    @Test
    public void testShareLockCompleteRelease() throws Exception {
        String key = "completeReleaseKey" + UUID.randomUUID();

        // 1. 三个客户端获得共享锁
        String lock1 = getRedisHelper().requireShareLock(namespace, key, 10, false);
        String lock2 = getRedisHelper().requireShareLock(namespace, key, 10, false);
        String lock3 = getRedisHelper().requireShareLock(namespace, key, 10, false);

        assert StringTools.isNotBlank(lock1);
        assert StringTools.isNotBlank(lock2);
        assert StringTools.isNotBlank(lock3);

        // 2. 释放前两个锁
        boolean succ1 = getRedisHelper().releaseLock(namespace, key, lock1, false);
        boolean succ2 = getRedisHelper().releaseLock(namespace, key, lock2, false);
        assert succ1;
        assert succ2;

        // 3. 此时排它锁仍然无法获取（因为还有lock3）
        String exclusiveLock = getRedisHelper().requireLock(namespace, key, 10, false);
        assert StringTools.isBlank(exclusiveLock);

        // 4. 释放最后一个锁
        boolean succ3 = getRedisHelper().releaseLock(namespace, key, lock3, false);
        assert succ3;

        // 5. 现在排它锁可以获取了
        String exclusiveLock2 = getRedisHelper().requireLock(namespace, key, 10, false);
        assert StringTools.isNotBlank(exclusiveLock2);

        // 6. 清理
        boolean succ4 = getRedisHelper().releaseLock(namespace, key, exclusiveLock2, false);
        assert succ4;
    }

}
