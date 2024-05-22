package com.pugwoo.wooutils.redis;

import com.pugwoo.wooutils.redis.exception.ExceedRateLimitException;
import com.pugwoo.wooutils.redis.impl.JsonRedisObjectConverter;
import com.pugwoo.wooutils.utils.ClassUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.mvel2.MVEL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@EnableAspectJAutoProxy
@Aspect
@Order(3000)
public class RedisLimitAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisLimitAspect.class);

    @Autowired
    private RedisHelper redisHelper;

    /**
     * 这里是多个加锁的实现
     */
    @Around("@annotation(com.pugwoo.wooutils.redis.RateLimits) execution(* *.*(..))")
    public Object arounds(ProceedingJoinPoint pjp) throws Throwable {
        // if not set redis, process method
        if (this.redisHelper == null) {
            LOGGER.error("redisHelper is null, RedisLimitAspect will pass through all method call");
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method targetMethod = signature.getMethod();
        RateLimits limits = targetMethod.getAnnotation(RateLimits.class);
        RateLimit[] value = limits.value();

        for (RateLimit rateLimit : value) {
            String key = genKey(rateLimit.keyScript(), pjp.getArgs());
            if (!requireLock(rateLimit, key, rateLimit.throwExceptionIfExceedRateLimit(), targetMethod, rateLimit.customExceptionMessage())) {
                return null;
            }
        }
        return pjp.proceed();
    }

    @Around("@annotation(com.pugwoo.wooutils.redis.RateLimit) execution(* *.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // if not set redis, process method
        if (this.redisHelper == null) {
            LOGGER.error("redisHelper is null, RedisLimitAspect will pass through all method call");
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method targetMethod = signature.getMethod();
        RateLimit limits = targetMethod.getAnnotation(RateLimit.class);

        String key = genKey(limits.keyScript(), pjp.getArgs());
        if (requireLock(limits, key, limits.throwExceptionIfExceedRateLimit(), targetMethod, limits.customExceptionMessage())) {
            return pjp.proceed();
        } else {
            return null;
        }
    }

    private String genKey(String keyScript, Object[] args) {
        String key = "-";
        if (!keyScript.trim().isEmpty()) {
            Map<String, Object> context = new HashMap<>();
            context.put("args", args);
            try {
                Object result = MVEL.eval(keyScript.trim(), context);
                if (result != null) {
                    key = result.toString();
                }
            } catch (Throwable e) {
                LOGGER.error("eval keyScript fail, keyScript:{}, args:{}",
                        keyScript, JsonRedisObjectConverter.toJson(args));
            }
        }
        return key;
    }

    private boolean requireLock(RateLimit rateLimit, String key, boolean throwExceptionIfExceedRateLimit,
                                Method targetMethod, String customExceptionMessage) {
        RedisLimitParam limitParam = new RedisLimitParam();

        String namespace = rateLimit.namespace();
        if (namespace.trim().isEmpty()) {
            namespace = generateNamespace(targetMethod);
        }

        limitParam.setNamespace(namespace);
        limitParam.setLimitCount(rateLimit.limitCount());
        limitParam.setLimitPeriod(rateLimit.limitPeriod());
        boolean result = redisHelper.useLimitCount(limitParam, key, 1) > 0; // 默认每次调用算1次，这里暂不需要作为配置

        // 如果阻塞等待，则进行阻塞等待尝试
        if (rateLimit.waitMillisecond() > 0) {
            // 构造兔子数列
            int a = 0, b = 1;
            long start = System.currentTimeMillis();

            while (true) {
                long totalWait = System.currentTimeMillis() - start;
                if (totalWait >= rateLimit.waitMillisecond()) {
                    break;
                }

                result = redisHelper.useLimitCount(limitParam, key, 1) > 0;
                if (result) {
                    break;
                }

                if (rateLimit.waitMillisecond() - totalWait < b) {
                    try {
                        Thread.sleep(rateLimit.waitMillisecond() - totalWait);
                    } catch (InterruptedException ignored) {}
                } else {
                    try {
                        Thread.sleep(b);
                    } catch (InterruptedException ignored) {
                    }
                    int c = a + b; // 构造兔子数列
                    a = b;
                    b = c;
                    if (b > 1000) {
                        b = 1000;
                    }
                }
            }
        }

        if (!result && throwExceptionIfExceedRateLimit) {
            if (customExceptionMessage != null && !customExceptionMessage.isEmpty()) {
                throw new ExceedRateLimitException(targetMethod, key, limitParam, customExceptionMessage);
            } else {
                throw new ExceedRateLimitException(targetMethod, key, limitParam);
            }
        }
        return result;
    }

    private String generateNamespace(Method method) {
        return ClassUtils.getMethodSignatureWithClassName(method);
    }

}
