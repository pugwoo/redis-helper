package com.pugwoo.wooutils.redis;

import com.pugwoo.wooutils.redis.exception.ExceedRateLimitException;

import java.lang.annotation.*;

/**
 * 限频器，限制单位周期内的调用次数。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /**
     * 分布式锁命名空间，每个业务有自己唯一的分布式锁namespace，相同的namespace表示同一把锁。<br>
     * 如果不指定，则使用"${全限定类名}.${方法名}[:${方法参数全限定类名1}[,${方法参数全限定类名2}][,...]]" <br>
     * <br>
     * 默认命名空间示例: <br>
     *    方法: public String helloByDefaultNamespace(String name, int i); <br>
     *    命名空间: com.example.HelloService.testHelloByDefaultNamespace:java.lang.String,int
     */
    String namespace() default "";

    /**
     * [可选] 分布式锁的不同的key的mvel表达式脚本，可以从参数列表变量args中获取
     * <br>
     * 例如，分布式锁注解在方法void foo(int a, String b) 上，那么设置keyScript为 args[0]+args[1] 来实现不同参数a/b不同锁。
     * <br>
     * @return 【重要】如果脚本执行出错，则打log，并等价于空字符串，并不会抛出异常阻止调用进行
     */
    String keyScript() default "";

    /**
     * [必须] 频率控制的周期
     */
    RedisLimitPeriodEnum limitPeriod();

    /**
     * [必须] 单位周期内的调用次数上限
     */
    int limitCount();

    /**
     * 当进程/线程没有拿到调用资格时，阻塞等待的时间，单位毫秒，默认10000毫秒
     * （取10秒这个值考虑的是人类等待计算机反馈的不耐烦的大概时间）。
     * 如果不需要阻塞，请设置为0.
     * @return bool
     */
    int waitMillisecond() default 10000;

    /**
     * 是否在获取不到调用资格时抛出异常 <br>
     * 默认抛出异常 <br>
     * 如果设置为true，则当获取不到资格时，抛出 {@link ExceedRateLimitException}
     * 如果设置为false，则限频的方法将不执行，直接返回null
     */
    boolean throwExceptionIfExceedRateLimit() default true;

    /**
     * 抛出异常时的自定义消息，当throwExceptionIfExceedRateLimit=true时生效
     */
    String customExceptionMessage() default "";

}
