package com.pugwoo.wooutils.redis.exception;

import com.pugwoo.wooutils.redis.Synchronized;
import com.pugwoo.wooutils.redis.impl.RedisLock;

import java.lang.reflect.Method;

/**
 * @author sapluk <br>
 * 如果获取不到分布式锁，且 {@link Synchronized#throwExceptionIfNotGetLock()} 设置为true
 * 则抛出该异常
 */
public class NotGetLockException extends RuntimeException {
    
    /** 执行的目标方法 */
    private final Method targetMethod;
    
    /** 分布式锁命名空间 {@link Synchronized#namespace()} */
    private final String namespace;
    
    /** 分布式锁的key，由 {@link Synchronized#keyScript()} 运算成功得出 */
    private final String key;
    
    public NotGetLockException(Method targetMethod, String namespace, String key) {
        super("Fail to require distributed lock, key:" + RedisLock.getKey(namespace, key) + ", targetMethod:" + targetMethod);
        this.targetMethod = targetMethod;
        this.namespace = namespace;
        this.key = key;
    }
    
    public Method getTargetMethod() {
        return targetMethod;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getKey() {
        return key;
    }
}
