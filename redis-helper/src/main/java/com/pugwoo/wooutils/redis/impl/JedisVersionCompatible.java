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

    // 标识现在运行的程序用的是哪个jedis版本, 2.x == 2, 3.x == 3, 4.x = 4
    private static final AtomicInteger jedisVer = new AtomicInteger(0);

    // START of setStringIfNotExist

    public static boolean setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 3) {
                return v3_setStringIfNotExist(jedis, key, expireSecond, value);
            } else if (_jedisVer == 4) {
                return v4_setStringIfNotExist(jedis, key, expireSecond, value);
            } else if (_jedisVer == 2) {
                return v2_setStringIfNotExist(jedis, key, expireSecond, value);
            } else {
                try {
                    boolean result = v3_setStringIfNotExist(jedis, key, expireSecond, value);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    try {
                        boolean result = v4_setStringIfNotExist(jedis, key, expireSecond, value);
                        jedisVer.set(4);
                        return result;
                    } catch (NoSuchMethodError | NoClassDefFoundError e2) {
                        boolean result = v2_setStringIfNotExist(jedis, key, expireSecond, value);
                        jedisVer.set(2);
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("operate jedis error, key:{}, value:{}", key, value, e);
            return false;
        }
    }

    private static final ExecutableAccessor compiledSetStringIfNotExist = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.set(key, value, \"NX\", \"EX\", expireSecond)");

    private static boolean v2_setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("value", value);
        params.put("expireSecond", expireSecond);
        params.put("jedis", jedis);

        Object result = MVEL.executeExpression(compiledSetStringIfNotExist, params); // 该方式对性能几乎没有影响
        return result != null;
    }

    private static boolean v3_setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        SetParams setParams = new SetParams();
        setParams.nx();
        setParams.ex(expireSecond);
        String result = jedis.set(key, value, setParams);
        return result != null;
    }

    private static final ExecutableAccessor getCompiledSetStringIfNotExistParamsEx = (ExecutableAccessor) MVEL.compileExpression(
            "setParams.ex(expireSecond)");

    private static boolean v4_setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        SetParams setParams = new SetParams();
        setParams.nx();

        Map<String, Object> params = new HashMap<>();
        params.put("setParams", setParams);
        params.put("expireSecond", (long) expireSecond);
        MVEL.executeExpression(getCompiledSetStringIfNotExistParamsEx, params);

        String result = jedis.set(key, value, setParams);
        return result != null;
    }

    // END of setStringIfNotExist

    // START of setString

    public static boolean setString(Jedis jedis, String key, int expireSecond, String value) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 4) {
                return v4_setString(jedis, key, expireSecond, value);
            } else if (_jedisVer ==2 || _jedisVer == 3) {
                return v3_setString(jedis, key, expireSecond, value);
            } else {
                try {
                    boolean result = v3_setString(jedis, key, expireSecond, value);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    boolean result = v4_setString(jedis, key, expireSecond, value);
                    jedisVer.set(4);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("setString operate jedis error, key:{}, value:{}", key, value, e);
            return false;
        }
    }

    private static boolean v3_setString(Jedis jedis, String key, int expireSecond, String value) {
        jedis.setex(key, expireSecond, value);
        return true;
    }

    private static final ExecutableAccessor compiledSetString = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.setex(key, expireSecond, value)");

    private static boolean v4_setString(Jedis jedis, String key, int expireSecond, String value) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("value", value);
        params.put("expireSecond", ((long) expireSecond));
        params.put("jedis", jedis);

        MVEL.executeExpression(compiledSetString, params);
        return true;
    }

    // END of setString

    // START of getExpireSecond

    public static long getExpireSecond(Jedis jedis, String key) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 4) {
                return v4_getExpireSecond(jedis, key);
            } else if (_jedisVer == 2 || _jedisVer == 3) {
                return v3_getExpireSecond(jedis, key);
            } else {
                try {
                    long result = v3_getExpireSecond(jedis, key);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    long result = v4_getExpireSecond(jedis, key);
                    jedisVer.set(4);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("getExpireSecond operate jedis error, key:{}", key, e);
            return -1;
        }
    }

    private static long v3_getExpireSecond(Jedis jedis, String key) {
        return jedis.ttl(key);
    }

    private static final ExecutableAccessor compiledGetExpireSecond = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.ttl(key)");

    private static long v4_getExpireSecond(Jedis jedis, String key) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("jedis", jedis);

        Object result = MVEL.executeExpression(compiledGetExpireSecond, params);
        return (long) result;
    }

    // END of getExpireSecond

    // START of setExpire

    public static boolean setExpire(Jedis jedis, String key, int expireSecond) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 4) {
                return v4_setExpire(jedis, key, expireSecond);
            } else if (_jedisVer == 2 || _jedisVer == 3) {
                return v3_setExpire(jedis, key, expireSecond);
            } else {
                try {
                    boolean result = v3_setExpire(jedis, key, expireSecond);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    boolean result = v4_setExpire(jedis, key, expireSecond);
                    jedisVer.set(4);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("setExpire operate jedis error, key:{}, expireSecond:{}", key, expireSecond, e);
            return false;
        }
    }

    private static boolean v3_setExpire(Jedis jedis, String key, int expireSecond) {
        jedis.expire(key, expireSecond);
        return true;
    }

    private static final ExecutableAccessor compiledSetExpire = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.expire(key, expireSecond)");

    private static boolean v4_setExpire(Jedis jedis, String key, int expireSecond) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("expireSecond", ((long) expireSecond));
        params.put("jedis", jedis);

        MVEL.executeExpression(compiledSetExpire, params);
        return true;
    }

    // END of setExpire

    // START of remove

    public static boolean remove(Jedis jedis, String key) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 4) {
                return v4_remove(jedis, key);
            } else if (_jedisVer == 2 || _jedisVer == 3) {
                return v3_remove(jedis, key);
            } else {
                try {
                    boolean result = v3_remove(jedis, key);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    boolean result = v4_remove(jedis, key);
                    jedisVer.set(4);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("remove operate jedis error, key:{}", key, e);
            return false;
        }
    }

    private static boolean v3_remove(Jedis jedis, String key) {
        jedis.del(key);
        return true;
    }

    private static final ExecutableAccessor compiledRemove = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.del(key)");

    private static boolean v4_remove(Jedis jedis, String key) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("jedis", jedis);

        MVEL.executeExpression(compiledRemove, params);
        return true;
    }

    // END of remove

    // START of incr

    public static long incr(Jedis jedis, String key) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 4) {
                return v4_incr(jedis, key);
            } else if (_jedisVer == 2 || _jedisVer == 3) {
                return v3_incr(jedis, key);
            } else {
                try {
                    long result = v3_incr(jedis, key);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    long result = v4_incr(jedis, key);
                    jedisVer.set(4);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("incr operate jedis error, key:{}", key, e);
            return -1;
        }
    }

    private static long v3_incr(Jedis jedis, String key) {
        return jedis.incr(key);
    }

    private static final ExecutableAccessor compiledIncr = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.incr(key)");

    private static long v4_incr(Jedis jedis, String key) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("jedis", jedis);

        Object result = MVEL.executeExpression(compiledIncr, params);
        return (long) result;
    }

    // END of incr

    // START of incrBy

    public static long incrBy(Jedis jedis, String key, long increment) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 4) {
                return v4_incrBy(jedis, key, increment);
            } else if (_jedisVer == 2 || _jedisVer == 3) {
                return v3_incrBy(jedis, key, increment);
            } else {
                try {
                    long result = v3_incrBy(jedis, key, increment);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    long result = v4_incrBy(jedis, key, increment);
                    jedisVer.set(4);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("incrBy operate jedis error, key:{}, increment:{}", key, increment, e);
            return -1;
        }
    }

    private static long v3_incrBy(Jedis jedis, String key, long increment) {
        return jedis.incrBy(key, increment);
    }

    private static final ExecutableAccessor compiledIncrBy = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.incrBy(key, increment)");

    private static long v4_incrBy(Jedis jedis, String key, long increment) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("increment", increment);
        params.put("jedis", jedis);

        Object result = MVEL.executeExpression(compiledIncrBy, params);
        return (long) result;
    }

    // END of incrBy

    // START of decr

    public static long decr(Jedis jedis, String key) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 4) {
                return v4_decr(jedis, key);
            } else if (_jedisVer == 2 || _jedisVer == 3) {
                return v3_decr(jedis, key);
            } else {
                try {
                    long result = v3_decr(jedis, key);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    long result = v4_decr(jedis, key);
                    jedisVer.set(4);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("decr operate jedis error, key:{}", key, e);
            return -1;
        }
    }

    private static long v3_decr(Jedis jedis, String key) {
        return jedis.decr(key);
    }

    private static final ExecutableAccessor compiledDecr = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.decr(key)");

    private static long v4_decr(Jedis jedis, String key) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("jedis", jedis);

        Object result = MVEL.executeExpression(compiledDecr, params);
        return (long) result;
    }

    // END of decr

    // START of decrBy

    public static long decrBy(Jedis jedis, String key, long decrement) {
        try {
            int _jedisVer = jedisVer.get();
            if (_jedisVer == 4) {
                return v4_decrBy(jedis, key, decrement);
            } else if (_jedisVer == 2 || _jedisVer == 3) {
                return v3_decrBy(jedis, key, decrement);
            } else {
                try {
                    long result = v3_decrBy(jedis, key, decrement);
                    jedisVer.set(3);
                    return result;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    long result = v4_decrBy(jedis, key, decrement);
                    jedisVer.set(4);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("decrBy operate jedis error, key:{}, decrement:{}", key, decrement, e);
            return -1;
        }
    }

    private static long v3_decrBy(Jedis jedis, String key, long decrement) {
        return jedis.decrBy(key, decrement);
    }

    private static final ExecutableAccessor compiledDecrBy = (ExecutableAccessor) MVEL.compileExpression(
            "jedis.decrBy(key, decrement)");

    private static long v4_decrBy(Jedis jedis, String key, long decrement) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", key);
        params.put("decrement", decrement);
        params.put("jedis", jedis);

        Object result = MVEL.executeExpression(compiledDecrBy, params);
        return (long) result;
    }

    // END of decrBy



}
