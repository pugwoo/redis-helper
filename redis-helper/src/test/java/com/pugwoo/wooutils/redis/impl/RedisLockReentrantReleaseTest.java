package com.pugwoo.wooutils.redis.impl;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.exception.NoJedisConnectionException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可重入锁在最外层释放失败后，不能再把线程本地计数当成“还握着锁”。
 */
public class RedisLockReentrantReleaseTest {

    @Test
    public void reentrantAcquireDoesNotTouchRedisUntilOuterRelease() {
        FakeRedis redis = new FakeRedis();
        RedisHelper helper = redis.helper();
        String namespace = ns();
        String key = "reentry";

        String first = RedisLock.requireLock(helper, namespace, key, 10, true);
        String second = RedisLock.requireLock(helper, namespace, key, 10, true);

        assertEquals(first, second);
        assertEquals(1, redis.setNxCount.get());
        assertEquals(0, redis.getCount.get());

        assertTrue(RedisLock.releaseLock(helper, namespace, key, first, true));
        assertEquals(0, redis.getCount.get());
        assertEquals(first, redis.data.get(redisKey(namespace, key)));

        assertTrue(RedisLock.releaseLock(helper, namespace, key, first, true));
        assertEquals(1, redis.getCount.get());
        assertNull(redis.data.get(redisKey(namespace, key)));
        assertTrue(!RedisLock.releaseLock(helper, namespace, key, first, true));
    }

    @Test
    public void failedReleaseCanBeRetried() {
        FakeRedis redis = new FakeRedis();
        RedisHelper helper = redis.helper();
        String namespace = ns();
        String key = "retry";

        String uuid = RedisLock.requireLock(helper, namespace, key, 10, true);
        redis.failConnections = true;
        assertTrue(!RedisLock.releaseLock(helper, namespace, key, uuid, true));
        assertEquals(uuid, redis.data.get(redisKey(namespace, key)));

        redis.failConnections = false;
        assertTrue(RedisLock.releaseLock(helper, namespace, key, uuid, true));
        assertNull(redis.data.get(redisKey(namespace, key)));

        String again = RedisLock.requireLock(helper, namespace, key, 10, true);
        assertNotEquals(uuid, again);
        assertEquals(again, redis.data.get(redisKey(namespace, key)));
    }

    @Test
    public void removeReturningFalseIsNotReportedAsSuccessAndCanBeRetried() {
        FakeRedis redis = new FakeRedis();
        RedisHelper helper = redis.helper();
        String namespace = ns();
        String key = "remove-false";

        String uuid = RedisLock.requireLock(helper, namespace, key, 10, true);
        // 模拟真实RedisHelperImpl.remove：GET已成功，但Lua删除执行异常被吞成false，而不是抛异常
        redis.removeReturnsFalse = true;
        assertTrue(!RedisLock.releaseLock(helper, namespace, key, uuid, true));
        assertEquals(uuid, redis.data.get(redisKey(namespace, key)));

        // pending不能被清掉：再次释放应真正删掉锁，而不是因本地无计数直接返回false
        redis.removeReturnsFalse = false;
        assertTrue(RedisLock.releaseLock(helper, namespace, key, uuid, true));
        assertNull(redis.data.get(redisKey(namespace, key)));

        String again = RedisLock.requireLock(helper, namespace, key, 10, true);
        assertNotEquals(uuid, again);
        assertEquals(again, redis.data.get(redisKey(namespace, key)));
        assertTrue(RedisLock.releaseLock(helper, namespace, key, again, true));
        assertNull(redis.data.get(redisKey(namespace, key)));
    }

    @Test
    public void removeReturningFalseThenLockTakenByOtherNodeDoesNotBypass() throws Exception {
        FakeRedis redis = new FakeRedis();
        RedisHelper helper = redis.helper();
        String namespace = ns();
        String key = "remove-false-expire";

        String uuid = RedisLock.requireLock(helper, namespace, key, 10, true);
        redis.removeReturnsFalse = true;
        assertTrue(!RedisLock.releaseLock(helper, namespace, key, uuid, true));
        redis.removeReturnsFalse = false;

        // 锁随后过期并被其他节点拿到：本线程不能凭pending中的旧uuid绕过Redis直接加锁
        redis.data.remove(redisKey(namespace, key));
        String otherUuid = requireOnOtherThread(helper, namespace, key);
        assertNotEquals(uuid, otherUuid);

        assertNull(RedisLock.requireLock(helper, namespace, key, 10, true));
        assertEquals(otherUuid, redis.data.get(redisKey(namespace, key)));
    }

    @Test
    public void removeReturningFalseForNonReentrantLockAlsoReturnsFalse() {
        FakeRedis redis = new FakeRedis();
        RedisHelper helper = redis.helper();
        String namespace = ns();
        String key = "remove-false-non-reentrant";

        String uuid = RedisLock.requireLock(helper, namespace, key, 10, false);
        redis.removeReturnsFalse = true;
        assertTrue(!RedisLock.releaseLock(helper, namespace, key, uuid, false));
        assertEquals(uuid, redis.data.get(redisKey(namespace, key)));

        redis.removeReturnsFalse = false;
        assertTrue(RedisLock.releaseLock(helper, namespace, key, uuid, false));
        assertNull(redis.data.get(redisKey(namespace, key)));
    }

    @Test
    public void failedReleaseDoesNotBypassWhenLockExpiredAndTakenByOtherNode() throws Exception {
        FakeRedis redis = new FakeRedis();
        RedisHelper helper = redis.helper();
        String namespace = ns();
        String key = "expire";

        String uuid = RedisLock.requireLock(helper, namespace, key, 10, true);
        redis.failConnections = true;
        assertTrue(!RedisLock.releaseLock(helper, namespace, key, uuid, true));

        redis.failConnections = false;
        redis.data.remove(redisKey(namespace, key));
        String otherUuid = requireOnOtherThread(helper, namespace, key);
        assertNotEquals(uuid, otherUuid);

        int setNxBefore = redis.setNxCount.get();
        assertNull(RedisLock.requireLock(helper, namespace, key, 10, true));
        assertNull(RedisLock.requireLock(helper, namespace, key, 10, true));
        assertTrue(redis.setNxCount.get() > setNxBefore);
        assertEquals(otherUuid, redis.data.get(redisKey(namespace, key)));
    }

    @Test
    public void failedReleaseThenRedisStillDownDoesNotReturnOldUuid() {
        FakeRedis redis = new FakeRedis();
        RedisHelper helper = redis.helper();
        String namespace = ns();
        String key = "down";

        String uuid = RedisLock.requireLock(helper, namespace, key, 10, true);
        redis.failConnections = true;
        assertTrue(!RedisLock.releaseLock(helper, namespace, key, uuid, true));

        assertThrows(NoJedisConnectionException.class,
                () -> RedisLock.requireLock(helper, namespace, key, 10, true));
        assertEquals(uuid, redis.data.get(redisKey(namespace, key)));
    }

    @Test
    public void failedReleaseIsRepairedByNextAcquireWhenRedisRecovers() throws Exception {
        FakeRedis redis = new FakeRedis();
        RedisHelper helper = redis.helper();
        String namespace = ns();
        String key = "repair";

        String uuid = RedisLock.requireLock(helper, namespace, key, 10, true);
        redis.failConnections = true;
        assertTrue(!RedisLock.releaseLock(helper, namespace, key, uuid, true));
        redis.failConnections = false;

        assertNull(requireOnOtherThread(helper, namespace, key));

        String again = RedisLock.requireLock(helper, namespace, key, 10, true);
        assertNotEquals(uuid, again);
        assertEquals(again, redis.data.get(redisKey(namespace, key)));
        assertEquals(again, RedisLock.requireLock(helper, namespace, key, 10, true));
        assertNull(requireOnOtherThread(helper, namespace, key));

        assertTrue(RedisLock.releaseLock(helper, namespace, key, again, true));
        assertTrue(RedisLock.releaseLock(helper, namespace, key, again, true));
        assertNull(redis.data.get(redisKey(namespace, key)));
    }

    @Test
    public void pendingReleaseOfOneKeyDoesNotAffectAnotherKey() throws Exception {
        FakeRedis redis = new FakeRedis();
        RedisHelper helper = redis.helper();
        String namespace = ns();

        String uuidA = RedisLock.requireLock(helper, namespace, "A", 10, true);
        String uuidB = RedisLock.requireLock(helper, namespace, "B", 10, true);
        redis.failConnections = true;
        assertTrue(!RedisLock.releaseLock(helper, namespace, "A", uuidA, true));
        redis.failConnections = false;

        assertEquals(uuidB, RedisLock.requireLock(helper, namespace, "B", 10, true));
        assertTrue(RedisLock.releaseLock(helper, namespace, "B", uuidB, true));
        assertTrue(RedisLock.releaseLock(helper, namespace, "B", uuidB, true));

        redis.data.remove(redisKey(namespace, "A"));
        String other = requireOnOtherThread(helper, namespace, "A");
        assertNull(RedisLock.requireLock(helper, namespace, "A", 10, true));
        assertEquals(other, redis.data.get(redisKey(namespace, "A")));
    }

    private static String requireOnOtherThread(RedisHelper helper, String namespace, String key) throws Exception {
        AtomicReference<String> uuid = new AtomicReference<String>();
        AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    uuid.set(RedisLock.requireLock(helper, namespace, key, 10, true));
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        thread.start();
        thread.join();
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return uuid.get();
    }

    private static String ns() {
        return "ns-" + java.util.UUID.randomUUID();
    }

    private static String redisKey(String namespace, String key) {
        return namespace + ":" + key;
    }

    private static final class FakeRedis {
        private final Map<String, String> data = new ConcurrentHashMap<String, String>();
        private final AtomicInteger setNxCount = new AtomicInteger();
        private final AtomicInteger getCount = new AtomicInteger();
        private volatile boolean failConnections;
        /**模拟删除时Lua执行异常：真实remove实现会吞掉异常返回false，而不是抛异常*/
        private volatile boolean removeReturnsFalse;

        private RedisHelper helper() {
            return (RedisHelper) Proxy.newProxyInstance(
                    RedisHelper.class.getClassLoader(),
                    new Class<?>[]{RedisHelper.class},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("toString".equals(method.getName())) {
                                return "FakeRedis";
                            }
                            if ("hashCode".equals(method.getName())) {
                                return System.identityHashCode(proxy);
                            }
                            if ("equals".equals(method.getName())) {
                                return proxy == args[0];
                            }
                        }
                        return dispatch(method, args);
                    });
        }

        private Object dispatch(Method method, Object[] args) {
            String name = method.getName();
            if ("setStringIfNotExist".equals(name)) {
                return setNx((String) args[0], (String) args[2]);
            }
            if ("getString".equals(name)) {
                return get((String) args[0]);
            }
            if ("remove".equals(name) && args != null && args.length == 2) {
                return remove((String) args[0], (String) args[1]);
            }
            if ("setObject".equals(name)) {
                return true;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            return null;
        }

        private boolean setNx(String key, String value) {
            synchronized (data) {
                if (failConnections) {
                    throw new NoJedisConnectionException("down");
                }
                setNxCount.incrementAndGet();
                if (data.containsKey(key)) {
                    return false;
                }
                data.put(key, value);
                return true;
            }
        }

        private String get(String key) {
            synchronized (data) {
                getCount.incrementAndGet();
                if (failConnections) {
                    throw new NoJedisConnectionException("down");
                }
                return data.get(key);
            }
        }

        private boolean remove(String key, String value) {
            synchronized (data) {
                if (removeReturnsFalse) {
                    return false;
                }
                if (failConnections) {
                    throw new NoJedisConnectionException("down");
                }
                String current = data.get(key);
                if (value != null && value.equals(current)) {
                    data.remove(key);
                    return true;
                }
                return false;
            }
        }
    }
}
