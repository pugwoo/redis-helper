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



    long totalWaitTime;


    protected static RedisSyncRet successGetLock( String uuid, String lockUuid, long totalWaitTime) {
        return new RedisSyncRet(true,uuid, lockUuid, totalWaitTime);
    }

    protected static RedisSyncRet notGetLock(long totalWaitTime) {
        return new RedisSyncRet(false, totalWaitTime);
    }


    public RedisSyncRet(boolean successGetLock, long totalWaitTime) {
        this.successGetLock = successGetLock;
        this.totalWaitTime = totalWaitTime;
    }

    public RedisSyncRet(boolean successGetLock, String uuid, String lockUuid, long totalWaitTime) {
        this.successGetLock = successGetLock;
        this.lockUuid = lockUuid;
        this.totalWaitTime = totalWaitTime;
    }

}
