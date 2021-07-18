package com.pugwoo.wooutils.redis.sync;

import com.pugwoo.wooutils.TestSync;
import com.pugwoo.wooutils.redis.Synchronized;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ThrowIfNotGetLockTestService {
    
    /** 获取不到锁不抛异常 */
    @Synchronized(namespace = "notThrowIfNotGetLock", expireSecond = 2, waitLockMillisecond = 1000)
    public void notThrowIfNotGetLock(int a, long sleepMs) {
        System.out.println(TestSync.DTF.format(LocalDateTime.now()) + " 线程" + a + "开始执行");
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ignore) {
            }
        }
        System.out.println(TestSync.DTF.format(LocalDateTime.now()) + " 线程" + a + "执行结束");
    }
    
    /** 获取不到锁抛异常 - 异常提供了无参数构造器 */
    @Synchronized(namespace = "throwIfNotGetLock", expireSecond = 2, waitLockMillisecond = 500,
            throwExceptionIfNotGetLock = true)
    public void throwIfNotGetLock(long sleepMs) {
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
