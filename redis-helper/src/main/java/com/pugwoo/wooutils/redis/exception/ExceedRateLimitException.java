package com.pugwoo.wooutils.redis.exception;

import com.pugwoo.wooutils.redis.RateLimit;
import com.pugwoo.wooutils.redis.RedisLimitParam;
import com.pugwoo.wooutils.redis.impl.RedisLimit;

import java.lang.reflect.Method;

/**
 * @author sapluk <br>
 * 如果获取不到调用资格，且 {@link RateLimit#throwExceptionIfExceedRateLimit()} 设置为true
 * 则抛出该异常
 */
public class ExceedRateLimitException extends RuntimeException {

    /** 执行的目标方法 */
    private final Method targetMethod;

    private final RedisLimitParam limitParam;

    /** 限频器的key，由 {@link RateLimit#keyScript()} 运算成功得出 */
    private final String key;

    public ExceedRateLimitException(Method targetMethod, String key, RedisLimitParam limitParam,
                                    String customExceptionMsg) {
        super(customExceptionMsg);
        this.targetMethod = targetMethod;
        this.limitParam = limitParam;
        this.key = key;
    }

    public ExceedRateLimitException(Method targetMethod, String key, RedisLimitParam limitParam) {
        super("Exceed rate limit, key:" + RedisLimit.getKey(limitParam, key) + ", targetMethod:" + targetMethod);
        this.targetMethod = targetMethod;
        this.limitParam = limitParam;
        this.key = key;
    }

    public Method getTargetMethod() {
        return targetMethod;
    }

    public String getKey() {
        return key;
    }

    public RedisLimitParam getLimitParam() {
        return limitParam;
    }

}
