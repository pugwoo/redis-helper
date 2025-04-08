package com.pugwoo.wooutils.redis;

/**
 * @author firefly
 */
public class RedisSyncRet {

    boolean successGetLock;

    /**
     * redis是否宕机或网络不通
     */
    boolean isRedisDown;

    /**
     * 心跳的 uuid
     */
    String uuid;

    /**
     * 锁的uuid
     */
    String lockUuid;

    /**
     * 等待锁的时间
     */
    long waitCost;

    /**
     * 尝试加锁的次数
     */
    long waitNum;

    protected static RedisSyncRet successGetLock(String uuid, String lockUuid, long waitCost, long waitNum, boolean isRedisDown) {
        return new RedisSyncRet(true, uuid, lockUuid, waitCost,waitNum, isRedisDown);
    }

    protected static RedisSyncRet notSuccessGetLock(long waitCost, long waitNum, boolean isRedisDown) {
        return new RedisSyncRet(false, waitCost,waitNum, isRedisDown);
    }


    public RedisSyncRet(boolean successGetLock, String uuid, String lockUuid,
                        long waitCost, long waitNum, boolean isRedisDown) {
        this(successGetLock,waitCost,waitNum, isRedisDown);
        this.uuid = uuid;
        this.lockUuid = lockUuid;
    }

    public RedisSyncRet(boolean successGetLock, long waitCost, long waitNum, boolean isRedisDown) {
        this.successGetLock = successGetLock;
        this.waitCost = waitCost;
        this.waitNum = waitNum;
        this.isRedisDown = isRedisDown;
    }
}
