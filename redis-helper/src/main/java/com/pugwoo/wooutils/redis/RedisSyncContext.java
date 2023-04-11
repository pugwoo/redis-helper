package com.pugwoo.wooutils.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 记录最后一次@Synchronized的执行结果信息
 *
 * @author nick, firefly
 */
public class RedisSyncContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSyncContext.class);


    @SuppressWarnings("AlibabaThreadLocalShouldRemove")
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
     * 这里没有用 ThreadLocal， 因此是多个线程的总共耗时
     * 这里统计多个锁一起获取成功相关信息:
     * 1. 总成功锁数: getTotalLockNum
     * 2. 时间: getTotalLockCost
     * 3. 平均时间: getTotalLockCost / getTotalLockNum
     */
    private static int getTotalLockNum = 0;
    private static long getTotalLockCost = 0;

    public synchronized static void recordSuccessTotalLockCost(long totalCost) {
        getTotalLockCost += totalCost;
        getTotalLockNum++;
    }

    /**
     * 第 n 个锁， 尝试的次数
     * 最高记录 5 个锁的情况
     */
    private static final long[] getOneLockNum = new long[5];
    private static final long[] getOneLockCost = new long[5];

    /**
     * 设置第i次的耗时信息：
     *
     * @param waitTime 单个锁等待的次数，目前是兔子数列
     * @param waitCost 单个锁等待的总时间消耗
     */
    protected synchronized static void recordOneLockCost(int i, long waitTime, long waitCost) {
        if (i >= getOneLockNum.length) {
            return;
        }
        getOneLockNum[i] += waitTime;
        getOneLockCost[i] += waitCost;
    }

    public static void printCostInfo() {
        if (getTotalLockNum != 0) {
            LOGGER.info("getTotalLockCost: {}, getTotalLockNum: {}, avgGetTotalLockCost: {}",
                    getTotalLockCost, getTotalLockNum, getTotalLockCost / getTotalLockNum);
        }
        for (int i = 0; i < getOneLockNum.length; i++) {
            if (getOneLockNum[i] == 0) {
                continue;
            }
            LOGGER.info("i:{}, getOneLockCost: {}, getOneLockNum: {}, avgGetOneLockCost: {}",
                    i, getOneLockCost[i], getOneLockNum[i], getOneLockCost[i] / getOneLockNum[i]);
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

}
