package com.pugwoo.wooutils.redis;

/**
 * @author sapluk <br>
 * 如果设置的 {@link Synchronized#throwExceptionIfNotGetLock()}
 * 未提供无参数构造器，或者出现设置异常信息时出错，
 * 都将使用该异常代替
 */
public class NotGetLockException extends RuntimeException {
    
    public NotGetLockException(String message) {
        super(message);
    }
}
