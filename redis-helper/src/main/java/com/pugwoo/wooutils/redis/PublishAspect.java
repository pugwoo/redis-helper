package com.pugwoo.wooutils.redis;

import com.pugwoo.wooutils.redis.impl.JsonRedisObjectConverter;
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

/**
 * Publish 注解的切面实现
 * 
 * @author pugwoo
 */
@EnableAspectJAutoProxy
@Aspect
@Order(4000)
public class PublishAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublishAspect.class);

    @Autowired
    private RedisHelper redisHelper;

    /**
     * 处理多个 @Publish 注解
     */
    @Around("@annotation(com.pugwoo.wooutils.redis.Publishs) execution(* *.*(..))")
    public Object arounds(ProceedingJoinPoint pjp) throws Throwable {
        // if not set redis, process method
        if (this.redisHelper == null) {
            LOGGER.error("redisHelper is null, PublishAspect will pass through all method call");
            return pjp.proceed();
        }

        // 先执行方法
        Object result = pjp.proceed();

        // 方法成功返回后，发布消息
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method targetMethod = signature.getMethod();
        Publishs publishs = targetMethod.getAnnotation(Publishs.class);
        Publish[] publishArray = publishs.value();

        for (Publish publish : publishArray) {
            publishMessage(publish, pjp.getArgs(), result);
        }

        return result;
    }

    /**
     * 处理单个 @Publish 注解
     */
    @Around("@annotation(com.pugwoo.wooutils.redis.Publish) execution(* *.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // if not set redis, process method
        if (this.redisHelper == null) {
            LOGGER.error("redisHelper is null, PublishAspect will pass through all method call");
            return pjp.proceed();
        }

        // 先执行方法
        Object result = pjp.proceed();

        // 方法成功返回后，发布消息
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method targetMethod = signature.getMethod();
        Publish publish = targetMethod.getAnnotation(Publish.class);

        publishMessage(publish, pjp.getArgs(), result);

        return result;
    }

    /**
     * 发布消息
     * 
     * @param publish 注解
     * @param args 方法参数
     * @param ret 方法返回值
     */
    private void publishMessage(Publish publish, Object[] args, Object ret) {
        String channel = publish.channel();
        String msgScript = publish.msgScript();

        // 执行 msgScript 获取消息内容
        String msgContent = evalMsgScript(msgScript, args, ret);
        if (msgContent == null) {
            LOGGER.error("eval msgScript fail or return null, msgScript:{}, args:{}, ret:{}, message will not be published",
                    msgScript, JsonRedisObjectConverter.toJson(args), JsonRedisObjectConverter.toJson(ret));
            return;
        }

        // 发布消息
        try {
            Long subscribers = redisHelper.publish(channel, msgContent);
            if (subscribers != null) {
                LOGGER.info("publish message success, channel:{}, subscribers:{}, msg:{}", channel, subscribers, msgContent);
            } else {
                LOGGER.error("publish message fail, channel:{}, msg:{}", channel, msgContent);
            }
        } catch (Exception e) {
            LOGGER.error("publish message error, channel:{}, msg:{}", channel, msgContent, e);
        }
    }

    /**
     * 执行 msgScript 脚本
     * 
     * @param msgScript mvel 表达式
     * @param args 方法参数
     * @param ret 方法返回值
     * @return 消息内容，如果执行失败返回 null
     */
    private String evalMsgScript(String msgScript, Object[] args, Object ret) {
        if (msgScript == null || msgScript.trim().isEmpty()) {
            LOGGER.error("msgScript is empty");
            return null;
        }

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("args", args);
            context.put("ret", ret);

            Object result = MVEL.eval(msgScript.trim(), context);
            if (result == null) {
                return null;
            }
            return result.toString();
        } catch (Throwable e) {
            LOGGER.error("eval msgScript fail, msgScript:{}, args:{}, ret:{}",
                    msgScript, JsonRedisObjectConverter.toJson(args), JsonRedisObjectConverter.toJson(ret), e);
            return null;
        }
    }

}

