package com.pugwoo.wooutils.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * 记录最后一次@Synchronized的执行结果信息
 *
 * @author nick, firefly
 */
public class RedisSyncContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSyncContext.class);

    private static final ThreadLocal<RedisSyncContext> CONTEXT_TL = new ThreadLocal<>();

    /**
     * 是否是串行执行，当redisHelper没有提供时为false。一般不需要来检查这个值。
     */
    private boolean isSync;

    /**
     * 是否有执行方法
     */
    private boolean haveRun;

    /**
     * 是否成功释放了分布式锁
     */
    private boolean isReleaseLockSuccess;

    /**
     * 这里没有用 ThreadLocal， 因此是多个线程的总共耗时
     * 这里统计多个锁一起获取成功相关信息:
     * 1. 总成功锁数: getTotalLockNum
     * 2. 时间: getTotalLockCost
     * 3. 平均时间: getTotalLockCost / getTotalLockNum
     */
    private static final AtomicInteger getTotalLockNum = new AtomicInteger(0);
    private static final AtomicLong getTotalLockCost = new AtomicLong(0);

    public static void recordSuccessTotalLockCost(long totalCost) {
        getTotalLockNum.incrementAndGet();
        getTotalLockCost.addAndGet(totalCost);
    }

    public static void printCostInfo() {
        if (getTotalLockNum.get() != 0) {
            LOGGER.info("getTotalLockCost: {}, getTotalLockNum: {}, avgGetTotalLockCost: {}",
                    getTotalLockCost, getTotalLockNum, getTotalLockCost.get() / getTotalLockNum.get());
        }
    }

    /**
     * 设置最后一次执行的结果信息。必须一次性设置完
     */
    protected static void set(boolean isSync, boolean haveRun) {
        RedisSyncContext context = new RedisSyncContext();
        context.isSync = isSync;
        context.haveRun = haveRun;

        CONTEXT_TL.set(context);
    }

    protected static void setIsReleaseLockSuccess(boolean isReleaseLockSuccess) {
        RedisSyncContext redisSyncContext = CONTEXT_TL.get();
        if (redisSyncContext != null) {
            redisSyncContext.isReleaseLockSuccess = isReleaseLockSuccess;
        }
    }

    /**
     * 是否有执行了方法
     *
     * @return bool
     */
    public static boolean getHaveRun() {
        RedisSyncContext context = CONTEXT_TL.get();
        if (context == null) {
            return false;
        }
        return context.haveRun;
    }

    /**
     * 是否是串行执行，当redisHelper没有提供时为false。一般不需要来检查这个值。
     *
     * @return ret
     */
    public static boolean getIsSync() {
        RedisSyncContext context = CONTEXT_TL.get();
        if (context == null) {
            return false;
        }
        return context.isSync;
    }

    /**
     * 是否成功释放了分布式锁
     */
    public static boolean getIsReleaseLockSuccess() {
        RedisSyncContext context = CONTEXT_TL.get();
        if (context == null) {
            return false;
        }
        return context.isReleaseLockSuccess;
    }
}
