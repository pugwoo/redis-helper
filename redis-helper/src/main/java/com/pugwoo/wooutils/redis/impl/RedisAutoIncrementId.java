package com.pugwoo.wooutils.redis.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pugwoo.wooutils.redis.RedisHelper;

public class RedisAutoIncrementId {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RedisAutoIncrementId.class);

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
				Long id = jedis.incr(key);
				if (expireSeconds > 0) {
					// 这里可以拆分2条命令的原因是一般自增ID不会只调一次，因此一次的失败并不会引起问题。
					// 最差的情况下，就是这个自增ID key永久存在，也不会有逻辑上的错误。
					jedis.expire(key, (long) expireSeconds);
				}
				return id;
			} catch (Exception e) {
				LOGGER.error("getAutoIncrementId jedis incr error, namespace:{}", namespace, e);
				return null;
			}
		});
	}
	
}
