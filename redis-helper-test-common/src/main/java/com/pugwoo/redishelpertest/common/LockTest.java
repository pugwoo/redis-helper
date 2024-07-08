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
                String uuid = getRedisHelper().requireLock("", randomUUidB, 10, true);
                assert StringTools.isNotBlank(uuid);
                isAnotherThreadGotLock.set(true);

                // 等待1秒后再加一次锁，此时时间线是第1.05秒
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                String uuid2 = getRedisHelper().requireLock("", randomUUidB, 10, true);
                assert StringTools.isNotBlank(uuid2);
                assert uuid.equals(uuid2);

                // 等待2秒后再解锁，此时时间线是第3.1秒
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                boolean succ = getRedisHelper().releaseLock("", randomUUidB, uuid, true);
                assert succ;

                // 等待2秒后再解锁，此时时间线是第5.1秒
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                boolean succ2 = getRedisHelper().releaseLock("", randomUUidB, uuid, true);
                assert succ2;

                // 再继续解锁，就失败了
                boolean succ3 = getRedisHelper().releaseLock("", randomUUidB, uuid, true);
                assert !succ3;
            }
        });
        thread.start();

        Thread.sleep(500);
        // 此时时间线是第0.5秒，另外一个线程已经拿到了keyB的锁

        assert isAnotherThreadGotLock.get(); // 如果这里不通过，上面的sleep可以加长一些

        String uuid = getRedisHelper().requireLock("", randomUUidA, 10, true);
        assert StringTools.isNotBlank(uuid);

        String uuid2 = getRedisHelper().requireLock("", randomUUidB, 10, true);
        assert StringTools.isBlank(uuid2);

        // 再加一次randomUUidA，可以拿得到
        String uuid3 = getRedisHelper().requireLock("", randomUUidA, 10, true);
        assert StringTools.isNotBlank(uuid3);
        assert uuid.equals(uuid3);

        // 如果以不可重入的方式再拿randomUUidA，是拿不到的
        String uuid4 = getRedisHelper().requireLock("", randomUUidA, 10, false);
        assert StringTools.isBlank(uuid4);

        // 等待1秒之后，此时时间线是第1.5秒，randomUUidB的锁还没有释放，因此还加不了锁
        Thread.sleep(1000);
        String uuid5 = getRedisHelper().requireLock("", randomUUidB, 10, true);
        assert StringTools.isBlank(uuid5);

        // 等待2秒之后，此时时间线是第3.5秒，randomUUidB的锁还没有释放，因此还加不了锁
        Thread.sleep(2000);
        String uuid6 = getRedisHelper().requireLock("", randomUUidB, 10, true);
        assert StringTools.isBlank(uuid6);

        // 等待2秒之后，此时时间线是第5.5秒，randomUUidB的锁已经释放，因此可以加锁
        Thread.sleep(2000);
        String uuid7 = getRedisHelper().requireLock("", randomUUidB, 10, true);
        assert StringTools.isNotBlank(uuid7);
    }

    @Test
    public void testNotReentrant() throws Exception {
        String randomUUidA = UUID.randomUUID().toString();
        String uuid = getRedisHelper().requireLock("", randomUUidA, 10, false);
        assert StringTools.isNotBlank(uuid);

        // 不可重入时，再拿一次，是拿不到的
        String uuid2 = getRedisHelper().requireLock("", randomUUidA, 10, false);
        assert StringTools.isBlank(uuid2);
    }

    @Test
    public void test() throws Exception {
        final String nameSpace = "myname";
        final String key = "key";

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
        assert (end - start) <= THREAD * SLEEP + 3000; // 预留3秒的耗时
    }

}
