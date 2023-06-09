package com.pugwoo.wooutils.redis.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.pugwoo.wooutils.redis.*;
import org.mvel2.MVEL;
import org.mvel2.compiler.ExecutableAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 大部分实现时间: 2016年11月2日 15:10:21
 * @author nick
 */
public class RedisHelperImpl implements RedisHelper {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RedisHelperImpl.class);
	
	/**
	 * 删除key-value的lua脚本 <br>
	 *
	 * params: <br>
	 *     KEYS1: 被操作的redis key <br>
	 *     ARGV1: key对应的值      <br>
	 *
	 * return: <br>
	 *     1: 成功 删除1个 <br>
	 *     0: 失败 删除0个 (key不存在 / key-value不匹配 / key-value匹配后刚好失效) <br>
	 */
	private final static String REMOVE_KEY_VALUE_SCRIPT =
			"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
	
	/**
	 * lua脚本 CAS <br>
	 *
	 * params: <br>
	 *     KEYS1: 被操作的redis key<br>
	 *     ARGV1: 新的value<br>
	 *     ARGV2: 旧的value 只有旧value等于现有的value时，才会被设置<br>
	 *
	 * return: <br>
	 *     1 设置成功 <br>
	 *     0 设置失败 旧的value校验失败 <br>
	 */
	String COMPARE_AND_SET_SCRIPT = "" +
			"if redis.call('get', KEYS[1]) == ARGV[2] then redis.call('set', KEYS[1], ARGV[1]); return 1 else return 0 end";
	
	/**
	 * lua脚本 CAS并重设过期时间 <br>
	 *
	 * params: <br>
	 *     KEYS1: 被操作的redis key<br>
	 *     ARGV1: 新的value<br>
	 *     ARGV2: 旧的value 只有旧value等于现有的value时，才会被设置<br>
	 *     ARGV3: 过期时间，必须为一个合法的过期时间
	 *
	 * return: <br>
	 *     1 设置成功 <br>
	 *     0 设置失败 旧的value校验失败 <br>
	 */
	String COMPARE_AND_SET_EXPIRE_SCRIPT =
			"if redis.call('get', KEYS[1]) == ARGV[2] then redis.call('setex', KEYS[1], ARGV[3], ARGV[1]); return 1 else return 0 end";
	
	
	/**约定：当host为null或blank时，表示不初始化*/
	protected String host = null;
	
	private Integer port = 6379;
	private Integer maxConnection = 128;
	private String password = null;
	/**指定0~15哪个redis库*/
	private Integer database = 0;
	private boolean testOnBorrow = true;
	/**连接和读超时的毫秒数，由于redis性能非常高，这里没必要区分是连接超时还是读超时了*/
	private Duration timeout = Duration.ofMillis(2000);
	/**String和Object之间的转换对象*/
	private IRedisObjectConverter redisObjectConverter = new JsonRedisObjectConverter();
	
	/**
	 * 单例的JedisPool，懒加载初始化
	 */
	private volatile JedisPool pool;

    /**清理消息队列数据的现场，懒加载初始化*/
	private volatile RedisMsgQueue.RecoverMsgTask recoverMsgTask;
	
	private Jedis getJedisConnection() {
		if(pool == null) {
			synchronized (this) {
				if(pool == null && host != null && !host.trim().isEmpty()) {
					JedisPoolConfig poolConfig = new JedisPoolConfig();
					poolConfig.setMaxTotal(maxConnection); // 最大链接数
					poolConfig.setTestOnBorrow(testOnBorrow);
					// 还是先用这个废弃的方法，因为setMaxWait 2.11才有，而jedis 3.6.x还在用2.9版本，导致稍微低一点版本的jedis就用不了
					poolConfig.setMaxWaitMillis(timeout.toMillis());
					// poolConfig.setMaxWait(timeout);

					if(password != null && password.trim().isEmpty()) {
						password = null;
					}
					
					pool = new JedisPool(poolConfig, host, port,
							(int) timeout.toMillis(), password, database);
				}
			}
		}
		if(pool == null) {return null;}
		try {
			return pool.getResource();
		} catch (Exception e) {
			LOGGER.error("redis get jedis fail", e);
			throw new NoJedisConnectionException(e);
		}
	}

	@Override
	public boolean isOk() {
		Jedis jedis = null;
		try {
			jedis = getJedisConnection();
			if(jedis == null) {
				return false;
			}
			jedis.get("a"); // 随便拿一个值测下，没抛异常则表示成功
			return true;
		} catch (Exception e) {
			LOGGER.error("check redis isOk fail", e);
			return false;
		} finally {
			if(jedis != null) {
				try {
					jedis.close();
				} catch (Exception e) {
					LOGGER.error("close jedis error", e);
				}
			}
		}
	}

	@Override
	public <R> R execute(Function<Jedis, R> jedisToFunc) {
		Jedis jedis = null;
		try {
			jedis = getJedisConnection();
			return jedisToFunc.apply(jedis);
		} finally {
			if(jedis != null) {
				try {
					jedis.close();
				} catch (Exception e) {
					LOGGER.error("close jedis error", e);
				}
			}
		}
	}
	
	@Override
	public List<Object> executePipeline(Consumer<Pipeline> pipeline) {
		Jedis jedis = null;
		try {
			jedis = getJedisConnection();
			Pipeline jedisPipeline = jedis.pipelined();
			pipeline.accept(jedisPipeline);
			return jedisPipeline.syncAndReturnAll();
		} finally {
			if(jedis != null) {
				try {
					jedis.close();
				} catch (Exception e) {
					LOGGER.error("close jedis error", e);
				}
			}
		}
	}
	
	@Override
	public List<Object> executeTransaction(Consumer<Transaction> transaction, String... keys) {
		Jedis jedis = null;
		try {
			jedis = getJedisConnection();
            if (keys != null && keys.length > 0) {
                jedis.watch(keys);
            }
            
            Transaction jedisTransaction = jedis.multi();
            transaction.accept(jedisTransaction);
            return jedisTransaction.exec();
		} finally {
			if(jedis != null) {
				try {
					jedis.close();
				} catch (Exception e) {
					LOGGER.error("close jedis error", e);
				}
			}
		}
	}

	@Override
	public boolean rename(String oldKey, String newKey) {
		if(oldKey == null || newKey == null) {
			return false;
		}

		return execute(jedis -> {
			try {
				jedis.rename(oldKey, newKey);
				return true;
			} catch (Exception e) {
				LOGGER.error("rename operate jedis error, oldKey:{}, newKey:{}", oldKey, newKey, e);
				return false;
			}
		});
	}

	@Override
	protected void finalize() throws Throwable {
		if(pool != null && !pool.isClosed()) {
			pool.close();
		}
		super.finalize();
	}
	
	@Override
	public boolean setString(String key, int expireSecond, String value) {
		if(value == null) { // null值不需要设置
			return true;
		}
		return execute(jedis -> {
			try {
				jedis.setex(key, expireSecond, value);
				return true;
			} catch (Exception e) {
				LOGGER.error("setString operate jedis error, key:{}, value:{}", key, value, e);
				return false;
			}
		});
	}
	
	@Override
	public <T> boolean setObject(String key, int expireSecond, T value) {
		if(redisObjectConverter == null) {
			throw new RuntimeException("IRedisObjectConverter is null");
		}
		String v = redisObjectConverter.convertToString(value);
		return setString(key, expireSecond, v);
	}

	private ExecutableAccessor compiled = (ExecutableAccessor) MVEL.compileExpression(
			"jedis.set(key, value, \"NX\", \"EX\", expireSecond)");

	// 标识现在是哪个jedis版本, 2.x == 1, 3.x == 2
	private AtomicInteger jedisVer = new AtomicInteger(0);

	private boolean v2_setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
		Map<String, Object> params = new HashMap<>();
		params.put("key", key);
		params.put("value", value);
		params.put("expireSecond", expireSecond);
		params.put("jedis", jedis);

		//String result = jedis.set(key, value, "NX", "EX", expireSecond);
		Object result = MVEL.executeExpression(compiled, params); // 该方式对性能几乎没有影响
		return result != null;
	}

	private boolean v3_setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
		SetParams setParams = new SetParams();
		setParams.nx();
		setParams.ex(expireSecond);
		String result = jedis.set(key, value, setParams);
		return result != null;
	}

	@Override
	public boolean setStringIfNotExist(String key, int expireSecond, String value) {
		if(value == null) { // null值不需要设置
			return true;
		}
		return execute(jedis -> {
			try {
				int _jedisVer = jedisVer.get();
				if (_jedisVer == 2) {
					return v3_setStringIfNotExist(jedis, key, expireSecond, value);
				} else if (_jedisVer == 1) {
					return v2_setStringIfNotExist(jedis, key, expireSecond, value);
				} else {
					try {
						boolean result = v3_setStringIfNotExist(jedis, key, expireSecond, value);
						jedisVer.set(2);
						return result;
					} catch (NoSuchMethodError | NoClassDefFoundError e) { // 同时兼容2.x和3.x的写法
						boolean result = v2_setStringIfNotExist(jedis, key, expireSecond, value);
						jedisVer.set(1);
						return result;
					}
				}
			} catch (Exception e) {
				LOGGER.error("operate jedis error, key:{}, value:{}", key, value, e);
				return false;
			}
		});
	}
	
	@Override
	public boolean setExpire(String key, int expireSecond) {
		return execute(jedis -> {
			try {
				jedis.expire(key, expireSecond);
				return true;
			} catch (Exception e) {
				LOGGER.error("operate jedis error, key:{}", key, e);
				return false;
			}
		});
	}
	
	@Override
	public long getExpireSecond(String key) {
		return (long) execute(jedis -> {
			try {
				return jedis.ttl(key);
			} catch (Exception e) {
				LOGGER.error("operate jedis error, key:{}", key, e);
				return -999L;
			}
		});
	}
	
	@Override
	public String getString(String key) {
		return execute(jedis -> {
			try {
				String str = jedis.get(key);
				return str;
			} catch (Exception e) {
				LOGGER.error("operate jedis error, key:{}", key, e);
				return null;
			}
		});
	}
	
	@Override
	public <T> T getObject(String key, Class<T> clazz) {
		if(redisObjectConverter == null) {
			throw new RuntimeException("IRedisObjectConverter is null");
		}
		String value = getString(key);
		if(value == null) {
			return null;
		}
		
		return redisObjectConverter.convertToObject(value, clazz);
	}

    @Override
    public <T> T getObject(String key, Class<T> clazz, Class<?>... genericClasses) {
        if (redisObjectConverter == null) {
            throw new RuntimeException("IRedisObjectConverter is null");
        }
        String value = getString(key);
        if (value == null) {
            return null;
        }

        return redisObjectConverter.convertToObject(value, clazz, genericClasses);
    }

	@Override
	public <T> T getObject(String key, Class<T> clazz, TypeReference<T> typeReference) {
		if (redisObjectConverter == null) {
			throw new RuntimeException("IRedisObjectConverter is null");
		}
		String value = getString(key);
		if (value == null) {
			return null;
		}

		return redisObjectConverter.convertToObject(value, clazz, typeReference);
	}

	@Override
	public List<String> getStrings(List<String> keys) {
		if(keys == null || keys.isEmpty()) {
			return new ArrayList<>();
		}
		
		return execute(jedis -> {
			try {
				List<String> strs = jedis.mget(keys.toArray(new String[0]));
				return strs;
			} catch (Exception e) {
				LOGGER.error("operate jedis error, keys:{}", keys, e);
				return null;
			}
		});
	}
	
	@Override
	public <T> List<T> getObjects(List<String> keys, Class<T> clazz) {
		if(redisObjectConverter == null) {
			throw new RuntimeException("IRedisObjectConverter is null");
		}
		List<String> values = getStrings(keys);
		if(values == null) {
			return null;
		}

		List<T> result = new ArrayList<>();
		for(String value : values) {
			result.add(redisObjectConverter.convertToObject(value, clazz));
		}

		return result;
	}

	@Override
	public <T> List<T> getObjects(List<String> keys, Class<T> clazz, TypeReference<T> typeReference) {
		if (typeReference == null) {
			return getObjects(keys, clazz);
		}

		if(redisObjectConverter == null) {
			throw new RuntimeException("IRedisObjectConverter is null");
		}
		List<String> values = getStrings(keys);
		if(values == null) {
			return null;
		}

		List<T> result = new ArrayList<>();
		for(String value : values) {
			result.add(redisObjectConverter.convertToObject(value, clazz, typeReference));
		}

		return result;
	}

	@Override
	public ScanResult<String> getKeys(String cursor, String pattern, int count) {
		if(cursor == null) {
			cursor = "";
		}
		final String _cursor = cursor;
		return execute(jedis -> {
			try {
				ScanParams scanParams = new ScanParams();
				scanParams.match(pattern);
				scanParams.count(count);
				return jedis.scan(_cursor, scanParams);
			} catch (Exception e) {
				LOGGER.error("operate jedis SCAN error, pattern:{}, cursor:{}, count:{}",
						pattern, _cursor, count, e);
				return null;
			}
		});
	}

	@Override @Deprecated
	public Set<String> getKeys(String pattern) {
		return execute(jedis -> {
			try {
				return jedis.keys(pattern);
			} catch (Exception e) {
				LOGGER.error("operate jedis KEYS error, pattern:{}", pattern, e);
				return null;
			}
		});
	}
	
	@Override @Deprecated
	public Map<String, String> getStrings(String pattern) {
		Set<String> keys = getKeys(pattern);
		if(keys == null) return null;
		if(keys.isEmpty()) {
			return new HashMap<>();
		}
		
		List<String> keyList = new ArrayList<>(keys);
		List<String> vals = getStrings(keyList);
		if(vals == null) return null;
		if(keyList.size() != vals.size()) {
			return null;
		}
		Map<String, String> map = new HashMap<>();
		for(int i = 0; i < keyList.size(); i++) {
			map.put(keyList.get(i), vals.get(i));
		}
		
		return map;
	}
	
	@Override @Deprecated
	public <T> Map<String, T> getObjects(String pattern, Class<T> clazz) {
		if(redisObjectConverter == null) {
			throw new RuntimeException("IRedisObjectConverter is null");
		}
		
		Map<String, String> vals = getStrings(pattern);
		if(vals == null) return null;
		
		Map<String, T> result = new HashMap<>();
		for(Entry<String, String> e : vals.entrySet()) {
			result.put(e.getKey(), redisObjectConverter.
					convertToObject(e.getValue(), clazz));
		}
		
		return result;
	}
	
	@Override
	public boolean remove(String key) {
		return execute(jedis -> {
			try {
				jedis.del(key);
				return true;
			} catch (Exception e) {
				LOGGER.error("operate jedis error, key:{}", key, e);
				return false;
			}
		});
	}
	
	@Override
	public boolean remove(String key, String value) {
		return execute(jedis -> {
			try {
				Object eval = jedis.eval(REMOVE_KEY_VALUE_SCRIPT, 1, key, value);
				return "1".equals(eval.toString());
			} catch (Exception e) {
				LOGGER.error("operate jedis error, key:{}", key, e);
				return false;
			}
		});
	}
	
	@Override
	public boolean compareAndSet(String key, String value, String oldValue, Integer expireSeconds) {
		if(value == null) { // 不支持value设置为null
			return false;
		}
		
		boolean expire = expireSeconds != null && expireSeconds >= 0;
		
		// 如果旧值为null，则当做setnx处理，但是不支持setnx时不指定过期时间
		if (oldValue == null) {
			if (expire) {
				return setStringIfNotExist(key, expireSeconds, value);
			} else {
				LOGGER.error("compareAndSet expireSeconds must >= 0 if oldValue is null");
				return false;
			}
		}
		
		return execute(jedis -> {
			try {
				Object eval = expire
						? jedis.eval(COMPARE_AND_SET_EXPIRE_SCRIPT, 1, key, value, oldValue, expireSeconds.toString())
						: jedis.eval(COMPARE_AND_SET_SCRIPT, 1, key, value, oldValue);
				return "1".equals(eval.toString());
			} catch (Exception e) {
				LOGGER.error("compareAndSet error,key:{}, value:{}, oldValue:{}", key, value, oldValue, e);
				return false;
			}
		});
	}

	@Override
	public long getLimitCount(RedisLimitParam limitParam, String key) {
		return RedisLimit.getLimitCount(this, limitParam, key);
	}

	@Override
	public boolean hasLimitCount(RedisLimitParam limitParam, String key) {
		return RedisLimit.getLimitCount(this, limitParam, key) > 0;
	}

	@Override
	public long useLimitCount(RedisLimitParam limitEnum, String key) {
		return RedisLimit.useLimitCount(this, limitEnum, key);
	}

	@Override
	public long useLimitCount(RedisLimitParam limitParam, String key, int count) {
		return RedisLimit.useLimitCount(this, limitParam, key, count);
	}
	
	@Override
	public String requireLock(String namespace, String key, int maxTransactionSeconds, boolean isReentrantLock) {
		return RedisLock.requireLock(this, namespace, key, maxTransactionSeconds, isReentrantLock);
	}

	@Override
	public boolean renewalLock(String namespace, String key, String lockUuid, int maxTransactionSeconds) {
		return RedisLock.renewalLock(this, namespace, key, lockUuid, maxTransactionSeconds);
	}

	@Override
	public boolean releaseLock(String namespace, String key, String lockUuid, boolean isReentrantLock) {
		return RedisLock.releaseLock(this, namespace, key, lockUuid, isReentrantLock);
	}
	
	@Override
	public Long getAutoIncrementId(String namespace) {
		return RedisAutoIncrementId.getAutoIncrementId(this, namespace);
	}

	@Override
	public Long getAutoIncrementId(String namespace, int expireSeconds) {
		return RedisAutoIncrementId.getAutoIncrementId(this, namespace, expireSeconds);
	}

	private void addTopicToTask(String topic) {
		if(recoverMsgTask == null) {
			synchronized (RedisMsgQueue.RecoverMsgTask.class) {
				if(recoverMsgTask == null) {
					recoverMsgTask = new RedisMsgQueue.RecoverMsgTask(this);
					recoverMsgTask.setName("RedisMsgQueue.RecoverMsgTask");
					recoverMsgTask.start();
				}
			}
		}

		recoverMsgTask.addTopic(topic);
	}

	@Override
	public String send(String topic, String msg) {
		addTopicToTask(topic);
		return RedisMsgQueue.send(this, topic, msg);
	}

	@Override
	public String send(String topic, String msg, int defaultAckTimeoutSec) {
		addTopicToTask(topic);
		return RedisMsgQueue.send(this, topic, msg, defaultAckTimeoutSec);
	}
	
	@Override
	public List<String> sendBatch(String topic, List<String> msgList) {
		addTopicToTask(topic);
		return RedisMsgQueue.sendBatch(this, topic, msgList);
	}
	
	@Override
	public List<String> sendBatch(String topic, List<String> msgList, int defaultAckTimeoutSec) {
		addTopicToTask(topic);
		return RedisMsgQueue.sendBatch(this, topic, msgList, defaultAckTimeoutSec);
	}

	@Override
	public RedisMsg receive(String topic) {
		addTopicToTask(topic);
		return RedisMsgQueue.receive(this, topic);
	}

	@Override
	public RedisMsg receive(String topic, int waitTimeoutSec, Integer ackTimeoutSec) {
		addTopicToTask(topic);
		return RedisMsgQueue.receive(this, topic, waitTimeoutSec, ackTimeoutSec);
	}

	@Override
	public boolean ack(String topic, String msgUuid) {
		return RedisMsgQueue.ack(this, topic, msgUuid);
	}
	
	@Override
	public boolean nack(String topic, String msgUuid) {
		return RedisMsgQueue.nack(this, topic, msgUuid);
	}
	
	@Override
	public boolean removeTopic(String topic) {
		return RedisMsgQueue.removeTopic(this, topic);
	}

	@Override
	public RedisQueueStatus getQueueStatus(String topic) {
		return RedisMsgQueue.getQueueStatus(this, topic);
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		if(port != null && port >= 0) {
			this.port = port;
		}
	}
	
	public Integer getMaxConnection() {
		return maxConnection;
	}

	public void setMaxConnection(Integer maxConnection) {
		this.maxConnection = maxConnection;
	}

	public Integer getDatabase() {
		return database;
	}

	public void setDatabase(Integer database) {
		if(database != null && database >= 0) {
			this.database = database;
		}
	}

	public boolean isTestOnBorrow() {
		return testOnBorrow;
	}

	public void setTestOnBorrow(boolean testOnBorrow) {
		this.testOnBorrow = testOnBorrow;
	}

	public Duration getTimeout() {
		return timeout;
	}

	/**设置超时时间，由于redis性能很高，这里没必要区分是读超时还是连接超时了，默认是2000毫秒*/
	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	@Override
	public IRedisObjectConverter getRedisObjectConverter() {
		return redisObjectConverter;
	}

	public void setRedisObjectConverter(IRedisObjectConverter redisObjectConverter) {
		this.redisObjectConverter = redisObjectConverter;
	}
	
}
