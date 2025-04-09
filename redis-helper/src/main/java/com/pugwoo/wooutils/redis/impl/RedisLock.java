package com.pugwoo.wooutils.redis.impl;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.utils.InnerCommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author nick
 * redis锁，用于保证分布式系统同一时刻只有一个程序获得资源。
 *          对于指定的nameSpace，每次只有一个对象可以获得锁。
 * redis有个很好的特性，就是超时删除。非常合适在实际的项目场景中。
 * <br>
 * 【注意】分布式锁并不等同于分布式事务。
 *        我建议应尽量避免使用到分布式事务，应由分布式各系统自行进行数据修正工作。
 */
public class RedisLock {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RedisLock.class);

	/**key -> keyCount*/
	private static final ThreadLocal<Map<String, Integer>> lockCount = new ThreadLocal<>();
	/**key -> keyUuid*/
	private static final ThreadLocal<Map<String, String>> lockUuidTL = new ThreadLocal<>();

	public static String getKey(String namespace, String key) {
		return namespace + ":" + key;
	}

    /**
     * 获得一个名称为key的锁，redis保证同一时刻只有一个client可以获得锁。
     *
     * @param namespace 命名空间，每个应用独立的空间
     * @param key 业务key，redis将保证同一个namespace同一个key只有一个client可以拿到锁
     * @param maxTransactionSeconds 单位秒，必须大于0,拿到锁之后,预计多久可以完成这个事务，如果超过这个时间还没有归还锁，那么事务将失败
     * @return 如果加锁成功，返回锁的唯一识别字符，可用于解锁；如果加锁失败，则返回null
     */
    public static String requireLock(RedisHelper redisHelper, String namespace,
            String key, int maxTransactionSeconds, boolean isReentrantLock) {
        if (namespace == null || key == null || key.isEmpty() || maxTransactionSeconds <= 0) {
            LOGGER.error("requireLock with error params: namespace:{},key:{},maxTransactionSeconds:{}",
                    namespace, key, maxTransactionSeconds, new Exception());
            return null;
        }

		String newKey = getKey(namespace, key);

		if (isReentrantLock) {
			if (lockCount.get() == null) {
				lockCount.set(new HashMap<>());
			}
			if (lockUuidTL.get() == null) {
				lockUuidTL.set(new HashMap<>());
			}

			Integer lc = lockCount.get().get(newKey);
			String uuid = lockUuidTL.get().get(newKey);
			if (lc != null && lc >= 1 && uuid != null && !uuid.isEmpty()) {
				lockCount.get().put(newKey, lc + 1);
				/*
			     * 特别说明：可重入锁获得的锁不续期，理由：
			     * 1) 一般可重入锁和第一次获得锁的动作在同一个代码业务内，时间较短，且锁续期需要额外的成本，故不进行
			     * 2) 如果同一个代码业务内需要续期，可以调用renewalLock续期
				 */
				return uuid;
			}
		}

		//try {
            String uuid = UUID.randomUUID().toString();
            boolean result = redisHelper.setStringIfNotExist(newKey, maxTransactionSeconds, uuid);
			if (result) {
				if (isReentrantLock) {
					lockCount.get().put(newKey, 1);
					lockUuidTL.get().put(newKey, uuid);
				}
				recordLockInfo(redisHelper, newKey, maxTransactionSeconds);
				return uuid;
			} else {
				return null;
			}
        //} catch (Exception e) {
            // 这里可能有一种极端情况， redis 网络返回超时了，实际上redis的锁获取成功了
			// 这种情况暂不进行处理，等待锁超时，原因：
			// 1）低概率事情
			// 2）网络异常情况下，uuid实际上拿不到，也无法解锁
			// 3）此时网络情况可能较差，去解锁也不一定成功
			// 4）redis会超时解锁，系统会自动恢复，目前超时是30秒，可以接受
        //    LOGGER.error("requireLock error, namespace:{}, key:{}", namespace, key, e);
        //   return null;
        //}
    }

	/**
	 * 写入分布式锁的加锁者信息：ip、threadId
	 */
	private static void recordLockInfo(RedisHelper redisHelper, String newKey, int maxTransactionSeconds) {
		List<String> ipv4IPs = new ArrayList<>();
		try {
			ipv4IPs = InnerCommonUtils.getIpv4IPs();
		} catch (Exception e) {
			LOGGER.error("getIpv4IPs error", e);
		}

		String ip = String.join(";", ipv4IPs);
		Long threadId = Thread.currentThread().getId();
		Map<String, Object> info = new HashMap<>();
		info.put("clientIp", ip);
		info.put("clientThreadId", threadId);
		info.put("lockTimestamp", System.currentTimeMillis());
		redisHelper.setObject(newKey + ".lockInfo", maxTransactionSeconds, info);
	}

    /**
     * 续期锁，也即延长锁的过时时间，需要提供锁的uuid，
	 * 但是这里并不需要保持原子操作，也即可能存在极低概率的误续了别人的锁，但是没有关系，它不会一直续下去
     * @param namespace 命名空间，每个应用独立的空间
     * @param key 业务key，redis将保证同一个namespace同一个key只有一个client可以拿到锁
	 * @param lockUuid 加的锁的uuid，会检查续锁的人是否是加锁的人
     * @param maxTransactionSeconds 单位秒，必须大于0，续锁的时长
     * @return 续锁是否成功
     */
	public static boolean renewalLock(RedisHelper redisHelper, String namespace, String key,
									  String lockUuid, int maxTransactionSeconds) {
        if(namespace == null || key == null || key.isEmpty() || maxTransactionSeconds <= 0) {
            LOGGER.error("renewalLock with error params: namespace:{},key:{},maxTransactionSeconds:{}",
                    namespace, key, maxTransactionSeconds, new Exception());
            return false;
        }

        try {
            String newKey = getKey(namespace, key);
			String value = redisHelper.getString(newKey);
			if(value == null) {
				// 存在一种场景，在debug模式下，因为断点，所有线程都没有按时执行，导致redis锁被释放了，这时候就会出现这种情况
				// 这种情况属于debug本地开发情况，所以暂不进行处理
				LOGGER.error("renewalLock namespace:{}, key:{}, lock not exist, try to lock back", namespace, key);

				// 此时，尝试重新加锁，这里可能有一种极端情况，就是刚好这个锁是正常被释放的，此时重新加锁是多余的，但不会有严重的问题，等过期时间到了就会释放
				boolean result = redisHelper.setStringIfNotExist(newKey, maxTransactionSeconds, lockUuid);
				if (result) {
					LOGGER.info("renewalLock namespace:{}, key:{}, lock not exist, lock back success", namespace, key);
					recordLockInfo(redisHelper, newKey, maxTransactionSeconds);
				} else {
					LOGGER.error("renewalLock namespace:{}, key:{}, lock not exist, lock back fail", namespace, key);
				}

				return false;
			} else if (!value.equals(lockUuid)) {
				LOGGER.error("renewalLock namespace:{}, key:{}, lockUuid not match, given:{}, in redis:{}",
						namespace, key, lockUuid, value);
				return false;
			} else {
				// 虽然从查询uuid到实际去续期，中间可能发生了锁的变化，但是这个情况出现概率极低
				// 而且出现了也没有大的问题，只是帮另外一个锁续期了一次，后续也不会一直续期
				boolean result = redisHelper.setExpire(newKey, maxTransactionSeconds);
				if (result) {
					redisHelper.setExpire(newKey + ".lockInfo", maxTransactionSeconds);
				}
				return result;
			}
        } catch (Exception e) {
            LOGGER.error("renewalLock error, namespace:{}, key:{}", namespace, key, e);
            return false;
        }
    }
	
	/**
	 * 如果事务已经完成，则归还锁。
	 * @param namespace 命名空间，每个应用独立的空间
	 * @param key 业务key，redis将保证同一个namespace同一个key只有一个client可以拿到锁
	 * @param lockUuid 锁的uuid，必须提供正确的uuid才可以解锁
	 * @return 解锁成功返回true，失败返回false
	 */
	public static boolean releaseLock(RedisHelper redisHelper, String namespace, String key, String lockUuid,
									  boolean isReentrantLock) {
		if(namespace == null || key == null || key.isEmpty()) {
			LOGGER.error("requireLock with error params: namespace:{},key:{}",
					namespace, key, new Exception());
			return false;
		}
		String newKey = getKey(namespace, key);

		if (isReentrantLock) {
			if (lockCount.get() == null) {
				lockCount.set(new HashMap<>());
			}
			if (lockUuidTL.get() == null) {
				lockUuidTL.set(new HashMap<>());
			}

			Integer lc = lockCount.get().get(newKey);
			if (lc != null && lc > 1) {
				lockCount.get().put(newKey, lc - 1);
				return true;
			} else if (lc == null || lc <= 0) {
				return false; // 说明没有加锁，或者已经解锁了
			}
			// 剩下的就是 lc == 1
		}

		try {
			String value = redisHelper.getString(newKey);
			if(value == null) {
				LOGGER.warn("releaseLock namespace:{}, key:{}, lock not exist", namespace, key);
				if (isReentrantLock) {
					lockCount.get().remove(newKey);
					lockUuidTL.get().remove(newKey);
				}
				return true;
			} else if (value.equals(lockUuid)) {
				redisHelper.remove(newKey, lockUuid); // 这个是原子操作
				// 说明，此处不移除lockInfo，原因是它只是一个加锁信息，有过期时间，直接等待过期就可以了，也便于在锁是否的短时间内，根据其信息debug问题
				if (isReentrantLock) {
					lockCount.get().remove(newKey);
					lockUuidTL.get().remove(newKey);
				}
				return true; // 就算lock uuid不匹配，也说明这个锁不是属于自己了，返回true表示解锁成功了
			} else {
				LOGGER.error("releaseLock namespace:{}, key:{} fail, uuid not match, redis:{}, given:{}",
						namespace, key, value, lockUuid);
				if (isReentrantLock) { // 这个时候锁已经不是自己的了，所以要清理掉
					lockCount.get().remove(newKey);
					lockUuidTL.get().remove(newKey);
				}
				return false;
			}
		} catch (Exception e) {
			// 这里可能由于网络原因，redis锁还在，所以不清理lockCount，以允许再次releaseLock
			LOGGER.error("releaseLock error, namespace:{}, key:{}", namespace, key, e);
			return false;
		}
	}
	
}
