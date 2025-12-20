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
 * SendMsg 注解的切面实现
 * 
 * @author pugwoo
 */
@EnableAspectJAutoProxy
@Aspect
@Order(4000)
public class SendMsgAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendMsgAspect.class);

    @Autowired
    private RedisHelper redisHelper;

    /**
     * 处理多个 @SendMsg 注解
     */
    @Around("@annotation(com.pugwoo.wooutils.redis.SendMsgs) execution(* *.*(..))")
    public Object arounds(ProceedingJoinPoint pjp) throws Throwable {
        // if not set redis, process method
        if (this.redisHelper == null) {
            LOGGER.error("redisHelper is null, SendMsgAspect will pass through all method call");
            return pjp.proceed();
        }

        // 先执行方法
        Object result = pjp.proceed();

        // 方法成功返回后，发送消息
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method targetMethod = signature.getMethod();
        SendMsgs sendMsgs = targetMethod.getAnnotation(SendMsgs.class);
        SendMsg[] sendMsgArray = sendMsgs.value();

        for (SendMsg sendMsg : sendMsgArray) {
            sendMessage(sendMsg, pjp.getArgs(), result);
        }

        return result;
    }

    /**
     * 处理单个 @SendMsg 注解
     */
    @Around("@annotation(com.pugwoo.wooutils.redis.SendMsg) execution(* *.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // if not set redis, process method
        if (this.redisHelper == null) {
            LOGGER.error("redisHelper is null, SendMsgAspect will pass through all method call");
            return pjp.proceed();
        }

        // 先执行方法
        Object result = pjp.proceed();

        // 方法成功返回后，发送消息
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method targetMethod = signature.getMethod();
        SendMsg sendMsg = targetMethod.getAnnotation(SendMsg.class);

        sendMessage(sendMsg, pjp.getArgs(), result);

        return result;
    }

    /**
     * 发送消息
     * 
     * @param sendMsg 注解
     * @param args 方法参数
     * @param ret 方法返回值
     */
    private void sendMessage(SendMsg sendMsg, Object[] args, Object ret) {
        String topic = sendMsg.topic();
        int defaultAckTimeoutSec = sendMsg.defaultAckTimeoutSec();
        String msgScript = sendMsg.msgScript();

        // 执行 msgScript 获取消息内容
        String msgContent = evalMsgScript(msgScript, args, ret);
        if (msgContent == null) {
            LOGGER.error("eval msgScript fail or return null, msgScript:{}, args:{}, ret:{}, message will not be sent",
                    msgScript, JsonRedisObjectConverter.toJson(args), JsonRedisObjectConverter.toJson(ret));
            return;
        }

        // 发送消息
        try {
            String uuid = redisHelper.send(topic, msgContent, defaultAckTimeoutSec);
            if (uuid != null) {
                LOGGER.info("send message success, topic:{}, uuid:{}, msg:{}", topic, uuid, msgContent);
            } else {
                LOGGER.error("send message fail, topic:{}, msg:{}", topic, msgContent);
            }
        } catch (Exception e) {
            LOGGER.error("send message error, topic:{}, msg:{}", topic, msgContent, e);
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

