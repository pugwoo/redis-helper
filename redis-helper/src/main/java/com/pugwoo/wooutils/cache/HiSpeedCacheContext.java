package com.pugwoo.wooutils.cache;

/**
 * 用于控制高速缓存的上下文，目前包括以下功能：
 * 1) 强制刷新缓存，即不走缓存，然后将读取的数据更新到缓存中。
 */
public class HiSpeedCacheContext {

    /**
     * 标识本次查询是否强制刷新缓存
     */
    private static final ThreadLocal<Boolean> forceRefreshOnce = new ThreadLocal<>();

    /**
     * 尝试强制刷新缓存，如果刷新失败则走原来缓存，如果刷新成功则更新缓存。
     */
    private static final ThreadLocal<Boolean> tryForceRefreshOnce = new ThreadLocal<>();

    /**
     * 停止本次查询的缓存，即不走缓存，也不更新缓存
     */
    private static final ThreadLocal<Boolean> disableOnce = new ThreadLocal<>();

    /**
     * 尝试强制刷新缓存，如果刷新失败则走原来缓存，如果刷新成功则更新缓存。这个设置只生效一次。
     */
    public static void tryForceRefreshOnce() {
        tryForceRefreshOnce.set(true);
    }

    /**
     * 设置本次缓存走强制刷新，每次设置不管最终调用成功还是失败，都只强制刷新一次，如需要继续强制刷新，需要再次设置。
     * 当强制刷新成功时，调用结果将更新到缓存中。
     */
    public static void forceRefreshOnce() {
        forceRefreshOnce.set(true);
    }

    /**
     * 设置本次缓存不走，即不走缓存，也不更新缓存。
     */
    public static void disableOnce() {
        disableOnce.set(true);
    }

    protected static boolean getTryForceRefresh() {
        Boolean b = tryForceRefreshOnce.get();
        if (b != null && b) {
            tryForceRefreshOnce.set(false); // 拿一次就清空
            return true;
        } else {
            return false;
        }
    }

    protected static boolean getForceRefresh() {
        Boolean b = forceRefreshOnce.get();
        if (b != null && b) {
            forceRefreshOnce.set(false); // 拿一次就清空
            return true;
        } else {
            return false;
        }
    }

    protected static boolean getDisable() {
        Boolean b = disableOnce.get();
        if (b != null && b) {
            disableOnce.set(false); // 拿一次就清空
            return true;
        } else {
            return false;
        }
    }

}
