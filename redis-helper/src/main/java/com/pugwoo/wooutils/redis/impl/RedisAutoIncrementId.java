package com.pugwoo.wooutils.redis.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pugwoo.wooutils.redis.RedisHelper;

public class RedisAutoIncrementId {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RedisAutoIncrementId.class);

	private static final String INCR_AND_EXPIRE_SCRIPT = "local value = redis.call('INCR', KEYS[1]); redis.call('EXPIRE', KEYS[1], ARGV[1]); return value";

	public static Long getAutoIncrementId(RedisHelper redisHelper, String namespace) {
		return getAutoIncrementId(redisHelper, namespace, 0);
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
}
