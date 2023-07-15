package com.pugwoo.wooutils.redis;

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
     * @return 【重要】如果脚本执行出错，则打log，并等价于空字符串，并不会抛出异常阻止调用进行
     */
    String keyScript() default "";

    /**
     * [必须] 频率控制的周期
     */
    RedisLimitPeroidEnum limitPeriod();

    /**
     * [必须] 单位周期内的调用次数上限
     */
    int limitCount();

}
