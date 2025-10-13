package com.pugwoo.redishelpertest.redis.sync;

import com.pugwoo.wooutils.redis.Synchronized;

import java.util.Date;

/**
 * 测试锁模式（排它锁和共享锁）的服务类
 */
public class LockModeTestService {

    /**
     * 使用排它锁（默认模式）
     * 同一时刻只有一个线程可以执行
     */
    @Synchronized(namespace = "exclusiveLockTest", expireSecond = 10, waitLockMillisecond = 5000)
    public String exclusiveLockMethod(String param) throws InterruptedException {
        System.out.println(new Date() + " [排它锁] 线程 " + Thread.currentThread().getName() + " 开始执行，参数: " + param);
        Thread.sleep(2000); // 模拟业务处理
        System.out.println(new Date() + " [排它锁] 线程 " + Thread.currentThread().getName() + " 执行完成");
        return "exclusive-" + param;
    }

    /**
     * 使用共享锁
     * 多个线程可以同时执行（读操作）
     */
    @Synchronized(namespace = "shareLockTest", mode = "share", expireSecond = 10, waitLockMillisecond = 5000)
    public String shareLockMethod(String param) throws InterruptedException {
        System.out.println(new Date() + " [共享锁] 线程 " + Thread.currentThread().getName() + " 开始执行，参数: " + param);
        Thread.sleep(2000); // 模拟读取操作
        System.out.println(new Date() + " [共享锁] 线程 " + Thread.currentThread().getName() + " 执行完成");
        return "share-" + param;
    }

    /**
     * 使用排它锁进行写操作
     */
    @Synchronized(namespace = "readWriteTest", mode = "exclusive", expireSecond = 10, waitLockMillisecond = 5000)
    public void writeData(String data) throws InterruptedException {
        System.out.println(new Date() + " [写操作-排它锁] 线程 " + Thread.currentThread().getName() + " 开始写入: " + data);
        Thread.sleep(1000);
        System.out.println(new Date() + " [写操作-排它锁] 线程 " + Thread.currentThread().getName() + " 写入完成");
    }

    /**
     * 使用共享锁进行读操作
     */
    @Synchronized(namespace = "readWriteTest", mode = "share", expireSecond = 10, waitLockMillisecond = 5000)
    public String readData() throws InterruptedException {
        System.out.println(new Date() + " [读操作-共享锁] 线程 " + Thread.currentThread().getName() + " 开始读取");
        Thread.sleep(1000);
        System.out.println(new Date() + " [读操作-共享锁] 线程 " + Thread.currentThread().getName() + " 读取完成");
        return "data";
    }

    /**
     * 使用共享锁，带心跳机制
     */
    @Synchronized(namespace = "shareLockWithHeartbeat", mode = "share", heartbeatExpireSecond = 30, waitLockMillisecond = 5000, logDebug = true)
    public String shareLockWithHeartbeat(String param) throws InterruptedException {
        System.out.println(new Date() + " [共享锁-心跳] 线程 " + Thread.currentThread().getName() + " 开始执行，参数: " + param);
        Thread.sleep(3000);
        System.out.println(new Date() + " [共享锁-心跳] 线程 " + Thread.currentThread().getName() + " 执行完成");
        return "share-heartbeat-" + param;
    }

    /**
     * 使用共享锁，带keyScript
     */
    @Synchronized(namespace = "shareLockWithKey", mode = "share", keyScript = "args[0]", expireSecond = 10, waitLockMillisecond = 5000)
    public String shareLockWithKey(String key, String value) throws InterruptedException {
        System.out.println(new Date() + " [共享锁-Key] 线程 " + Thread.currentThread().getName() + " 开始执行，key: " + key + ", value: " + value);
        Thread.sleep(1000);
        System.out.println(new Date() + " [共享锁-Key] 线程 " + Thread.currentThread().getName() + " 执行完成");
        return "share-" + key + "-" + value;
    }

    /**
     * 混合使用：先获取排它锁，再获取共享锁
     * 这种情况下，需要先释放排它锁，才能获取共享锁
     */
    @Synchronized(namespace = "mixedLock1", mode = "exclusive", expireSecond = 5, waitLockMillisecond = 3000)
    @Synchronized(namespace = "mixedLock2", mode = "share", expireSecond = 5, waitLockMillisecond = 3000)
    public String mixedLockMethod(String param) throws InterruptedException {
        System.out.println(new Date() + " [混合锁] 线程 " + Thread.currentThread().getName() + " 开始执行，参数: " + param);
        Thread.sleep(1000);
        System.out.println(new Date() + " [混合锁] 线程 " + Thread.currentThread().getName() + " 执行完成");
        return "mixed-" + param;
    }
}

