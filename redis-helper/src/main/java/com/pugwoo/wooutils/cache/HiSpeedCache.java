package com.pugwoo.wooutils.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 本地高速缓存
 * 1. 可以不依赖于redis。
 * 2. 因为是高速缓存，超时时间一般可以设置很短，例如10秒到几分钟
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface HiSpeedCache {

    // 说明：对于本地高速缓存，使用包名+类名+方法名+方法名hashCode作为namespace，因此不需要用户指定namespace

    /**
     * [可选] 高速缓存的不同的key的mvel表达式脚本，可以从参数列表变量args中获取<br>
     * <br>
     * 例如，分布式锁注解在方法void foo(int a, String b) 上，那么设置keyScript为 args[0]+args[1] 来实现不同参数a/b不同锁。
     * <br>
     * @return 【重要】如果脚本执行出错，则打log，然后直接调用方法，等价于缓存失效。如果脚本直接结果返回null，则等价于空字符
     */
    String keyScript() default "";

    /**
     * [可选] 是否走缓存的mvel表达式脚本，可以从参数列表变量args中获取<br>
     * 当为空时，等价于"true"，即走缓存；当返回true时，走缓存；
     * 当返回false时，不走缓存，直接调用目标方法；返回其它值时，打印异常log且不走缓存，直接调用目标方法<br>
     */
    String cacheConditionScript() default "";

    /**
     * 高速缓存的超时时间，默认1秒，建议使用1到10秒<br>
     * 当此值小于等于0时，等价于没有这个注解，不做缓存，直接调用目标方法
     */
    int expireSecond() default 1;

    /**
     * 提前fetch更新数据的时间比例，0.8表示刷新频率为expireSecond的80%。<br>
     * 例如expireSecond是60，那么实际刷新频率就是每60*0.8=48秒刷新一次<br>
     * 特别的，当preFetchRatio等于0时，表示不间断一直刷新。<br>
     * 当数值小于0或大于1时，设置无效，重置为0.8
     */
    double preFetchRatio() default 0.8;

    /**
     * 当缓存接口被访问时，自动设定后续自动刷新缓存的时间。缓存将以expireSecond的频率持续更新continueFetchSecond秒。<br>
     * continueFetchSecond必须大于0，否则不生效。一般来说，continueFetchSecond 大于 expireSecond。<br>
     * 如果后台刷新backend方法失败，内存缓存会仍然保留，直到超过continueFetchSecond为止。<br>
     * 注意：后台刷新会在缓存过期前提前触发（约在expireSecond的80%时间点），以避免缓存过期瞬间请求穿透。<br>
     */
    int continueFetchSecond() default 0;

    /**
     * 高速缓存执行方法更新时，是否并行。
     * 默认为否，此时一个缓存最多只会由一个线程执行，一定程度可以缓解当方法比较慢时，堵住整个线程池。
     * 如果设置为true时，即使相同方法参数调用卡主了，仍然会在线程池中发起，堵住整个线程池的风险更大些。
     */
    boolean concurrentFetch() default false;

    /**
     * 当N个相同key的请求同时进来时，第一个请求调用业务逻辑，其它请求最多等待cacheRebuildWaitMs毫秒复用第一个请求的结果，<br>
     * 如果等待时间超过cacheRebuildWaitMs毫秒，则不再等待，直接调用业务逻辑。<br>
     * 当值为0或小于0，则不等待，直接调用业务逻辑。
     */
    int cacheRebuildWaitMs() default 1000;

    /**
     * 是否json克隆返回数据，默认true<br>
     * 如果启动克隆，那么调用者对返回值进行修改，就不会影响缓存的值。<br>
     * 如果没有启动克隆，性能能达到最大。请注意，调用者如果修改了返回值，等于直接修改缓存的值，可能导致严重的bug，因此不建议修改返回值。
     */
    boolean cloneReturn() default true;

    /**
     * 指定自定义克隆对象的类，必须实现CustomCloner接口，该类必须是一个可以new的类，每次克隆时将new出一个类来负责克隆。
     */
    Class<?> customCloner() default void.class;

    /**
     * 是否使用redis保存数据，默认关闭。<br>
     * 只有当前是Spring容器且有RedisHelper的bean时，useRedis=true才生效，否则等价于useRedis=false，即便设置为true。<br>
     * 当使用useRedis=true时，cloneReturn选项失效。<br>
     * 当使用redis保存数据时，数据的失效时长为expireSecond的2倍。
     */
    boolean useRedis() default false;
    
    /**
     * 是否缓存null值，默认是false不缓存 <br>
     * 当此值为false时，方法返回null值，不进行缓存
     */
    boolean cacheNullValue() default false;
    
    /**
     * 当高速缓存使用了redis时，该参数有效 <br>
     *   默认每次都是去redis拿缓存数据 <br>
     *   当该参数大于0时，会将redis的缓存数据在本地缓存设置的时间，相当于二级缓存 <br>
     */
    int cacheRedisDataMillisecond() default 0;

}
