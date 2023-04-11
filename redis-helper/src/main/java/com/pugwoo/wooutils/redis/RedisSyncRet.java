package com.pugwoo.wooutils.redis;

/**
 * @author firefly
 */
public class RedisSyncRet {

    boolean successGetLock;

    /**
     * 心跳的 uuid
     */
    String uuid;

    /**
     * 锁的uuid
     */
    String lockUuid;

    long waitCost;
    long waitNum;


    protected static RedisSyncRet successGetLock(String uuid, String lockUuid, long waitCost, long waitNum) {
        return new RedisSyncRet(true, uuid, lockUuid, waitCost,waitNum);
    }

    protected static RedisSyncRet notSuccessGetLock(long waitCost, long waitNum) {
        return new RedisSyncRet(false, waitCost,waitNum);
    }


    public RedisSyncRet(boolean successGetLock, String uuid, String lockUuid, long waitCost, long waitNum) {
        this(successGetLock,waitCost,waitNum);
        this.uuid = uuid;
        this.lockUuid = lockUuid;
    }

    public RedisSyncRet(boolean successGetLock, long waitCost, long waitNum) {
        this.successGetLock = successGetLock;
        this.waitCost = waitCost;
        this.waitNum = waitNum;
    }
}
