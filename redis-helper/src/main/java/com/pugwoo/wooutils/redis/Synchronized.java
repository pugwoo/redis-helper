package com.pugwoo.wooutils.redis;

import com.pugwoo.wooutils.redis.exception.NotGetLockException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁，注解在方法上。<br>
 * 如果想拿到更多的信息，可以通过RedisSyncContext拿，线程独立。
 * @author nick markfly
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Synchronizeds.class)
public @interface Synchronized {

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
	 * 锁超时的秒数，如果使用者超过这个时间还没有主动释放锁，那么redis会自动释放掉该锁。
	 * 请使用者合理评估任务执行时间，推荐按正常执行时间的10倍~100倍评估该时间。
     * <br>
     * 当expireSecond大于0时有效，如果指定了expireSecond，则heartbeatSecond失效。
	 * @return bool
	 */
	int expireSecond() default 0;

    /**
     * 锁的心跳超时秒数，默认使用心跳机制。
     * 设置心跳秒数，方便任务执行时间不定的锁。
     * 默认30秒。分布式锁会每3秒钟向redis心跳一次。重要的任务可以将心跳超时时间设置长一些，例如300秒。
     * @return bool
     */
	int heartbeatExpireSecond() default 30;
	
	/**
	 * 当进程/线程没有拿到锁时，阻塞等待的时间，单位毫秒，默认10000毫秒
	 * （取10秒这个值考虑的是人类等待计算机反馈的不耐烦的大概时间）。
	 * 如果不需要阻塞，请设置为0.
	 * @return bool
	 */
	int waitLockMillisecond() default 10000;

	/**
	 * 是否开启记录获得锁和释放锁的日志，默认关闭
	 * @return bool
	 */
	boolean logDebug() default false;
	
	/**
	 * 是否在获取不到分布式锁时抛出异常 <br>
	 * 默认抛出异常 @since 1.3.0 <br>
	 * 如果设置为true，则当获取不到锁时，抛出 {@link NotGetLockException}
	 */
	boolean throwExceptionIfNotGetLock() default true;

	/**
	 * 是否是可重入锁，默认是可重入锁
	 */
	boolean isReentrantLock() default true;
}
