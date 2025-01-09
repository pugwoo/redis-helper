package com.pugwoo.wooutils.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface RedisHelper {

	/**
	 * 获得redisHelper的对象转换器
	 */
	IRedisObjectConverter getRedisObjectConverter();

	/**
	 * 检查redis是否已经准备就绪，包括ip端口、密码等是否已经正确，服务器端是否已经正常ping-pong
	 */
	boolean isOk();

	/**
	 * 传入jedis，然后自行实现逻辑，最后会自动关闭jedis资源。
	 * <p>
	 * 该方法用于替换原来getJedisConnection方法
	 * 
	 * @param jedisToFunc 执行redis操作
	 * @return 返回jedisToFunc的返回值
	 */
	<R> R execute(Function<Jedis, R> jedisToFunc);
	
	/**
	 * 按顺序执行pipeline，返回所有执行的结果列表
	 * @param pipeline 执行redis操作
	 */
	List<Object> executePipeline(Consumer<Pipeline> pipeline);
	
	/**
	 * 执行redis事务，keys是需要watch的key
	 * @param transaction 执行redis操作
	 * @param keys watch的key
	 */
	List<Object> executeTransaction(Consumer<Transaction> transaction, String ...keys);

	/**
	 * 重命名redis的key
	 * @param oldKey 原key
	 * @param newKey 如果newKey已存在，会覆盖掉
	 * @return 除非抛出异常，否则认为成功；不处理oldKey不存在的情况，认为是成功
	 */
	boolean rename(String oldKey, String newKey);

	/**
	 * 设置字符串
	 * @param key
	 * @param expireSecond
	 * @param value
	 * @return
	 */
	boolean setString(String key, int expireSecond, String value);
	
	/**
	 * 设置对象
	 * @param key
	 * @param expireSecond
	 * @param value
	 * @return
	 */
	<T> boolean setObject(String key, int expireSecond, T value);

	/**
	 * 当key不存在时才写入，写入成功返回true，写入失败返回false
	 * @param key
	 * @param expireSecond
	 * @param value
	 * @return
	 */
	boolean setStringIfNotExist(String key, int expireSecond, String value);
	
	/**
	 * 设置key的超时时间
	 * @param key redis的key
	 * @param expireSecond 超时时间，单位秒
	 * @return 设置成功返回true，否则返回false
	 */
	boolean setExpire(String key, int expireSecond);
	
	/**
	 * 获取key剩余的有效时间，秒
	 * @param key
	 * @return 如果没有设置超时，返回-1；如果key不存在，返回-2；如果有异常，返回-999
	 */
	long getExpireSecond(String key);
	
	/**
	 * 获取字符串，不存在或redis连接不上返回null
	 * @param key
	 * @return
	 */
	String getString(String key);
	
	/**
	 * 获取对象，需要提供IRedisObjectConverter的实现对象
	 * @param key redis key
	 * @return
	 */
	<T> T getObject(String key, Class<T> clazz);

	/**
	 * 获取对象，需要提供IRedisObjectConverter的实现对象【不支持嵌套泛型】
	 *
	 * @param key redis key
	 * @param genericClasses 支持泛型类，但不支持嵌套泛型，嵌套泛型请使用getObject传入TypeReference的方式
	 */
	<T> T getObject(String key, Class<T> clazz, Class<?>... genericClasses);

	/**
	 * 获取对象，需要提供
	 * @param key redis key
	 * @param typeReference 泛型，支持多个泛型和嵌套泛型
	 */
	<T> T getObject(String key, TypeReference<T> typeReference);

	/**
	 * 通过keys批量获得redis的key和值
	 * @param keys
	 * @return 个数和顺序和keys一直，如果key不存在，则其值为null。整个命令操作失败则返回null
	 */
	List<String> getStrings(List<String> keys);
	
	/**
	 * 通过keys批量获得redis的key和值【不支持泛型，泛型请用带typeReference参数的方法】
	 * @param keys
	 * @return 个数和顺序和keys一直，如果key不存在，则其值为null。整个命令操作失败则返回null
	 */
	<T> List<T> getObjects(List<String> keys, Class<T> clazz);

	/**
	 * 通过keys批量获得redis的key和值
	 * @param typeReference 泛型信息
	 * @return 个数和顺序和keys一直，如果key不存在，则其值为null。整个命令操作失败则返回null
	 */
	<T> List<T> getObjects(List<String> keys, TypeReference<T> typeReference);

	/**
	 * 删除指定的key
	 * @param key
	 * @return
	 */
	boolean remove(String key);
	
	/**
	 * key-value匹配的删除key操作
	 * @param key    key
	 * @param value  value 只有值相同才会成功删除
	 * @return
	 *   - true  删除成功
	 *   - false 删除失败 key不存在/key-value不匹配/key-value匹配后刚好失效
	 */
	boolean remove(String key, String value);
	
	/**
	 * CAS，成功返回true，失败返回false。
	 * 注意：在高并发场景下，过多线程使用该方法将导致过多无用的重试，从而大幅降低性能。
	 * @param key   key
	 * @param value 不支持设置为null，请使用 {@link #remove(String, String)}
	 * @param oldValue 旧的value 如果该值为null，则expireSeconds必须提供且大于等于0
	 * @param expireSeconds 超时时间，如果是null，则等于不改变，原来是多少秒就多少秒
	 */
	boolean compareAndSet(String key, String value, String oldValue, Integer expireSeconds);
	
	///////////////////// RedisLimit 限制次数 ///////////////////////
	
	/**
	 * 查询key的redis限制剩余次数。
	 * @param limitParam 限制参数
	 * @param key 业务主键
	 * @return -1是系统异常，正常值大于等于0
	 */
	long getLimitCount(RedisLimitParam limitParam, String key);
	
	/**
	 * 判断是否还有限制次数。
	 * @param limitParam
	 * @param key
	 * @return
	 */
	boolean hasLimitCount(RedisLimitParam limitParam, String key);
	
	/**
	 * 使用了一次限制。一般来说，业务都是在处理成功后才扣减使用是否成功的限制，
	 * 如果使用失败了，如果业务支持事务回滚，那么可以回滚掉，此时可以不用RedisTransaction做全局限制。
	 * 
	 * @param limitEnum
	 * @param key
	 * @return 返回是当前周期内第几个使用配额的，从1开始，如果返回-1，表示使用配额失败
	 */
	long useLimitCount(RedisLimitParam limitEnum, String key);
	
	/**
	 * 使用了count次限制。一般来说，业务都是在处理成功后才扣减使用是否成功的限制，
	 * 如果使用失败了，如果业务支持事务回滚，那么可以回滚掉，此时可以不用RedisTransaction做全局限制。
	 * 
	 * @param limitParam
	 * @param key
	 * @param count 一次可以使用掉多个count
	 * @return 返回是当前周期内第几个使用配额的，如果返回-1，表示使用配额失败
	 */
	long useLimitCount(RedisLimitParam limitParam, String key, int count);
	
	/////////////////// Redis Lock 分布式锁 ////////////////////////
	
	/**
	 * 获得一个名称为key的锁，redis保证同一时刻只有一个client可以获得锁。
	 * 
	 * @param namespace 命名空间，每个应用独立的空间
	 * @param key 业务key，redis将保证同一个namespace同一个key只有一个client可以拿到锁
	 * @param maxTransactionSeconds 单位秒，必须大于0,拿到锁之后,预计多久可以完成这个事务，如果超过这个时间还没有归还锁，那么事务将失败
	 * @param isReentrantLock 是否是可重入锁
	 * @return 如果加锁成功，返回锁的唯一识别字符，可用于解锁；如果加锁失败，则返回null
	 */
	String requireLock(String namespace, String key, int maxTransactionSeconds, boolean isReentrantLock);

	/**
	 * 续期锁的有效期
	 *
	 * @param namespace 命名空间，每个应用独立的空间
	 * @param key 业务key，redis将保证同一个namespace同一个key只有一个client可以拿到锁
	 * @param maxTransactionSeconds 单位秒，必须大于0，锁的有效期
	 * @param lockUuid 锁的uuid，提供对的uuid才进行续期
	 * @return 续期成功返回true，否则返回false
	 */
	boolean renewalLock(String namespace, String key, String lockUuid, int maxTransactionSeconds);

	/**
	 * 如果事务已经完成，则归还锁。
	 * @param namespace 命名空间，每个应用独立的空间
	 * @param key 业务key，redis将保证同一个namespace同一个key只有一个client可以拿到锁
	 * @param lockUuid 锁的uuid
	 * @param isReentrantLock 是否是可重入锁
	 * @return 释放锁成功时返回true，失败时返回false
	 */
	boolean releaseLock(String namespace, String key, String lockUuid, boolean isReentrantLock);
	
	/////////////////// Redis Auto Increment ID 分布式自增id //////////////////////
	
	/**
	 * 获得自增id，从1开始<br/>
	 * 说明：自增ID的namespace永久生效，如需设置自动过期清理时间，请使用getAutoIncrementId带expireSeconds参数的方法
	 * @param namespace 必须，由使用方自定决定，用于区分不同的业务。实际redis key会加上_ID后缀
	 * @return 获取失败(例如网络原因不通等)返回null，注意判断和重试
	 */
	Long getAutoIncrementId(String namespace);

	/**
	 * 批量获得自增id，从1开始<br/>
	 * 说明：自增ID的namespace永久生效，如需设置自动过期清理时间，请使用getAutoIncrementId带expireSeconds参数的方法
	 * @param namespace 必须，由使用方自定决定，用于区分不同的业务。实际redis key会加上_ID后缀
	 * @param batchNum 批量获取的数量
	 * @return 获取失败(例如网络原因不通等)返回null，注意判断和重试
	 */
	List<Long> getAutoIncrementIdBatch(String namespace, int batchNum);

	/**
	 * 获得自增id，从1开始
	 * @param namespace 必须，由使用方自定决定，用于区分不同的业务。实际redis key会加上_ID后缀
	 * @param expireSeconds 过期时间，大于0时生效；重新获取时会重新设置过期时间
	 * @return 获取失败(例如网络原因不通等)返回null，注意判断和重试
	 */
	Long getAutoIncrementId(String namespace, int expireSeconds);

	/**
	 * 批量获得自增id，从1开始<br/>
	 * @param namespace 必须，由使用方自定决定，用于区分不同的业务。实际redis key会加上_ID后缀
	 * @param expireSeconds 过期时间，大于0时生效；重新获取时会重新设置过期时间
	 * @param batchNum 批量获取的数量
	 * @return 获取失败(例如网络原因不通等)返回null，注意判断和重试
	 */
	List<Long> getAutoIncrementIdBatch(String namespace, int batchNum, int expireSeconds);

	/////////////////// Redis 带 ACK 机制的消息队列 ///////////////////////////////

	/**
	 * 发送消息，返回消息的uuid。默认的超时时间是86400秒
	 * @param topic topic将是redis的key
	 * @param msg
	 * @return
	 */
	String send(String topic, String msg);

	/**
	 * 发送消息，返回消息的uuid
	 * @param topic 即redis的key
	 * @param msg
	 * @param defaultAckTimeoutSec 默认ack超时时间：当消费者消费了消息却没来得及设置ack超时时间时的默认超时秒数。
	 * @return 消息的uuid，发送失败返回null
	 */
	String send(String topic, String msg, int defaultAckTimeoutSec);
	
	/**
	 * 批量发送消息，返回消息的uuid。默认的超时时间是86400秒
	 * @param topic topic将是redis的key
	 * @param msgList 消息列表
	 * @return 消息的uuidList，发送失败返回null
	 */
	List<String> sendBatch(String topic, List<String> msgList);
	
	/**
	 * 批量发送消息，返回消息的uuid
	 * @param topic 即redis的key
	 * @param msgList 消息列表
	 * @param defaultAckTimeoutSec 默认ack超时时间：当消费者消费了消息却没来得及设置ack超时时间时的默认超时秒数。
	 *                             建议处理时间默认比较长的应用，可以将该值设置较大，例如60秒或120秒
	 * @return 消息的uuidList，发送失败返回null
	 */
	List<String> sendBatch(String topic, List<String> msgList, int defaultAckTimeoutSec);
	
	/**
	 * 接收消息，永久阻塞式，使用默认发送方设置的actTimeout值
	 * @param topic 即redis的key
	 * @return
	 */
	RedisMsg receive(String topic);

	/**
	 * 接收消息
	 * @param topic 即redis的key
	 * @param waitTimeoutSec 指定接口阻塞等待时间，0表示不阻塞，-1表示永久等待，大于0为等待的秒数
	 * @param ackTimeoutSec ack确认超时的秒数，设置为null则表示不修改，使用发送方设置的默认超时值
	 * @return 如果没有接收到消息，返回null
	 */
	RedisMsg receive(String topic, int waitTimeoutSec, Integer ackTimeoutSec);

	/**
	 * 确认消费消息成功，删除消息
	 * @param topic 即redis的key
	 * @param msgUuid
	 * @return
	 */
	boolean ack(String topic, String msgUuid);
	
	/**
	 * 确认消费消息失败，复原消息
	 *   将消息复原后，可立即被消费
	 *   而超时的清理复原消息，被消费的频率会被超时时间控制
	 * @param topic 即redis的key
	 * @param msgUuid
	 * @return
	 */
	boolean nack(String topic, String msgUuid);
	
	/**
	 * 清理整个topic数据
	 * @param topic 即redis的key
	 * @return
	 */
	boolean removeTopic(String topic);

	/**
	 * 消息队列状态
	 * @param topic
	 * @return
	 */
	RedisQueueStatus getQueueStatus(String topic);

}
