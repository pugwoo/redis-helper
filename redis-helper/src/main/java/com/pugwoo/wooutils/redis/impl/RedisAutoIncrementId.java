package com.pugwoo.wooutils.redis.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pugwoo.wooutils.redis.RedisHelper;

import java.util.ArrayList;
import java.util.List;

public class RedisAutoIncrementId {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RedisAutoIncrementId.class);

	private static final String INCR_AND_EXPIRE_SCRIPT = "local value = redis.call('INCR', KEYS[1]); redis.call('EXPIRE', KEYS[1], ARGV[1]); return value";

	private static final String INCR_BY_AND_EXPIRE_SCRIPT = "local value = redis.call('INCRBY', KEYS[1], ARGV[1]); redis.call('EXPIRE', KEYS[1], ARGV[2]); return value";

	public static Long getAutoIncrementId(RedisHelper redisHelper, String namespace) {
		return getAutoIncrementId(redisHelper, namespace, 0);
	}

	public static List<Long> getAutoIncrementIdBatch(RedisHelper redisHelper, String namespace, int batchNum) {
		return getAutoIncrementIdBatch(redisHelper, namespace, batchNum, 0);
	}

	public static Long getAutoIncrementId(RedisHelper redisHelper, String namespace, int expireSeconds) {
		if(namespace == null || namespace.isEmpty()) {
			LOGGER.error("getAutoIncrementId with error params: namespace:{}",
					namespace, new Exception());
			return null;
		}

		return redisHelper.execute(jedis -> {
			try {
				String key = namespace + "_ID";
				if (expireSeconds <= 0) {
					return jedis.incr(key);
				} else {
					Object eval = jedis.eval(INCR_AND_EXPIRE_SCRIPT, 1, key, String.valueOf(expireSeconds));
					if (eval == null) {
						return null;
					} else {
						return eval instanceof Long ? (Long) eval : Long.valueOf(eval.toString());
					}
				}
			} catch (Exception e) {
				LOGGER.error("getAutoIncrementId jedis incr error, namespace:{}", namespace, e);
				return null;
			}
		});
	}

	public static List<Long> getAutoIncrementIdBatch(RedisHelper redisHelper, String namespace, int batchNum, int expireSeconds) {
		if(namespace == null || namespace.isEmpty()) {
			LOGGER.error("getAutoIncrementId with error params: namespace:{}",
					namespace, new Exception());
			return null;
		}

		Long id = redisHelper.execute(jedis -> {
			try {
				String key = namespace + "_ID";
				if (expireSeconds <= 0) {
					return jedis.incrBy(key, batchNum);
				} else {
					Object eval = jedis.eval(INCR_BY_AND_EXPIRE_SCRIPT, 1, key, String.valueOf(batchNum), String.valueOf(expireSeconds));
					if (eval == null) {
						return null;
					} else {
						return eval instanceof Long ? (Long) eval : Long.valueOf(eval.toString());
					}
				}
			} catch (Exception e) {
				LOGGER.error("getAutoIncrementId jedis incr error, namespace:{}", namespace, e);
				return null;
			}
		});

		if(id == null) {
			return null;
		}
		List<Long> result = new ArrayList<>();
		for(long i = id - batchNum + 1; i <= id; i++) {
			result.add(i);
		}
		return result;
	}
}
