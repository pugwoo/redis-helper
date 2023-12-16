package com.pugwoo.wooutils.redis.impl;

import org.mvel2.MVEL;
import org.mvel2.compiler.ExecutableAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 所有用于jedis版本兼容性代码
 */
public class JedisVersionCompatible {

    private static final Logger LOGGER = LoggerFactory.getLogger(JedisVersionCompatible.class);

    // 标识现在是哪个jedis版本, 2.x == 2, 3.x == 3, 4.x = 4
    private static final AtomicInteger jedisVer = new AtomicInteger(0);

    public static boolean setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 3) {
                return v3_setStringIfNotExist(jedis, key, expireSecond, value);
            } else if (_jedisVer == 2) {
                return v2_setStringIfNotExist(jedis, key, expireSecond, value);
            } else {
                try {
                    boolean result = v3_setStringIfNotExist(jedis, key, expireSecond, value);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) { // 同时兼容2.x和3.x的写法
                    boolean result = v2_setStringIfNotExist(jedis, key, expireSecond, value);
                    jedisVer.set(2);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("operate jedis error, key:{}, value:{}", key, value, e);
            return false;
        }
    }

    private static final ExecutableAccessor compiled = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.set(key, value, \"NX\", \"EX\", expireSecond)");

    private static boolean v2_setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("value", value);
        params.put("expireSecond", expireSecond);
        params.put("jedis", jedis);

        // String result = jedis.set(key, value, "NX", "EX", expireSecond);
        Object result = MVEL.executeExpression(compiled, params); // 该方式对性能几乎没有影响
        return result != null;
    }

    private static boolean v3_setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        SetParams setParams = new SetParams();
        setParams.nx();
        setParams.ex(expireSecond);
        String result = jedis.set(key, value, setParams);
        return result != null;
    }
}
