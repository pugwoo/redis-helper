package com.pugwoo.wooutils.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 本地高速缓存
 * 1. 可以不依赖于redis，因此，不需要序列化和反序列化。但是要【特别注意】缓存的值为Java的对象引用，也即返回值的使用者，如果修改了返回值，将等于直接修改了缓存的值，存在bug风险。此时可以开启cloneReturn
 * 2. 因为是高速缓存，超时时间很短，同时为了避免缓存穿透，因此一律缓存null值
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
     * 高速缓存的超时时间，默认1秒，建议使用1到10秒
     */
    int expireSecond() default 1;

    /**
     * 当缓存接口被访问时，自动设定后续自动刷新缓存的时间。缓存将以expireSecond的频率持续更新continueFetchSecond秒。
     */
    int continueFetchSecond() default 0;

    /**
     * 高速缓存执行方法更新时，是否并行。
     * 默认为否，此时一个缓存最多只会由一个线程执行，一定程度可以缓解当方法比较慢时，堵住整个线程池。
     * 如果设置为true时，即使相同方法参数调用卡主了，仍然会在线程池中发起，堵住整个线程池的风险更大些。
     */
    boolean concurrentFetch() default false;

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
