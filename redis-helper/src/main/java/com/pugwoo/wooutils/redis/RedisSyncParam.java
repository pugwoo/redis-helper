package com.pugwoo.wooutils.redis;


import org.aspectj.lang.ProceedingJoinPoint;

import java.lang.reflect.Method;

/**
 * @author firefly
 *
 *         封装参数
 * @see Synchronized
 */
public class RedisSyncParam {

    /**
     * @see Synchronized#namespace()
     */
    protected String namespace = "";

    /**
     * @see Synchronized#keyScript()
     */
    protected String keyScript = "";

    /**
     * @see Synchronized#expireSecond()
     */
    protected int expireSecond = 0;

    /**
     * @see Synchronized#heartbeatExpireSecond()
     *
     * 这个不应该少于心跳的间隔 3 秒
     */
    protected  int heartbeatExpireSecond = 30;

    /**
     * @see Synchronized#waitLockMillisecond()
     */
    protected int waitLockMillisecond = 10000;

    /**
     * @see Synchronized#logDebug()
     */
    protected boolean logDebug = false;

    /**
     * @see Synchronized#throwExceptionIfNotGetLock()
     */
    protected boolean throwExceptionIfNotGetLock = true;

    /**
     * @see Synchronized#isReentrantLock()
     */
    protected boolean isReentrantLock = true;

    /**
     * @see Synchronized#passThroughWhenRedisDown()
     */
    protected boolean passThroughWhenRedisDown = false;

    // #########################

    /**
     * 构造的key
     */
    protected  String key;

    /**
     * 原方法
     */
    protected Method targetMethod;

    /**
     * 注解的实例
     */
    protected  Synchronized sync;

    /**
     * spring 提供的 ProceedingJoinPoint
     */
    protected ProceedingJoinPoint pjp;

}
