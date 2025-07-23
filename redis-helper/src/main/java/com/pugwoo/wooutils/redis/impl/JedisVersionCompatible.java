package com.pugwoo.wooutils.redis.impl;

import org.mvel2.MVEL;
import org.mvel2.compiler.ExecutableAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 所有用于jedis版本兼容性代码
 */
public class JedisVersionCompatible {

    private static final Logger LOGGER = LoggerFactory.getLogger(JedisVersionCompatible.class);

    private static final int jedisVersion = getJedisVersion();

    /**
     * 通过pom.properties文件的方式查询jedis的版本
     * @return 返回0表示未知
     */
    private static int getJedisVersion() {
        String resource = "META-INF/maven/redis.clients/jedis/pom.properties";
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String version = p.getProperty("version");
                if (version == null) {
                    return 0;
                }
                int index = version.indexOf(".");
                if (index <= 0) {
                    return 0;
                }
                return Integer.parseInt(version.substring(0, index));
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    // 标识现在运行的程序用的是哪个jedis版本, 2.x == 2, 3.x == 3, 4.x = 4
    @Deprecated
    private static final AtomicInteger jedisVer = new AtomicInteger(0);

    // START of setStringIfNotExist

    public static boolean setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        try {
            if (jedisVersion == 2) {
                return v2_setStringIfNotExist(jedis, key, expireSecond, value);
            } else if (jedisVersion >= 3 && jedisVersion <= 5) {
                return v3v4v5_setStringIfNotExist(jedis, key, expireSecond, value);
            } else {
                return v6_setStringIfNotExist(jedis, key, expireSecond, value);
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
        return result != null && "OK".equals(result.toString());
    }

    // 在静态块中只查一次 Method
    private static final Method SET_PARAMS_EX_LONG;
    private static final Method SET_PARAMS_EX_INT;

    static {
        Method m = null;
        try {
            m = SetParams.class.getMethod("ex", long.class);
        } catch (Throwable ignored) {}
        SET_PARAMS_EX_LONG = m;

        try {
            m = SetParams.class.getMethod("ex", int.class);
        } catch (Throwable ignored) {}
        SET_PARAMS_EX_INT = m;
    }

    private static boolean v3v4v5_setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        SetParams setParams = new SetParams();
        setParams.nx();

        try {
            if (SET_PARAMS_EX_LONG != null) {
                SET_PARAMS_EX_LONG.invoke(setParams, (long) expireSecond);
            } else if (SET_PARAMS_EX_INT != null) {
                SET_PARAMS_EX_INT.invoke(setParams, expireSecond);
            } else {
                throw new RuntimeException("SetParams.ex() not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String result = jedis.set(key, value, setParams);
        return "OK".equals(result);
    }

    private static boolean v6_setStringIfNotExist(Jedis jedis, String key, int expireSecond, String value) {
        SetParams setParams = new SetParams();
        setParams.nx();
        setParams.ex(expireSecond);
        String result = jedis.set(key, value, setParams);
        return "OK".equals(result);
    }


    // END of setStringIfNotExist

    // START of setString

    public static boolean setString(Jedis jedis, String key, int expireSecond, String value) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_setString(jedis, key, expireSecond, value);
        } else {
            return v4v5v6_setString(jedis, key, expireSecond, value);
        }
    }

    private static final Method JEDIS_SET_EX_LONG;
    private static final Method JEDIS_SET_EX_INT;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("setex", String.class, long.class, String.class);
        } catch (Throwable ignored) {}
        JEDIS_SET_EX_LONG = m;

        try {
            m = Jedis.class.getMethod("setex", String.class, int.class, String.class);
        } catch (Throwable ignored) {}
        JEDIS_SET_EX_INT = m;
    }

    private static boolean v2v3_setString(Jedis jedis, String key, int expireSecond, String value) {
        try {
            if (JEDIS_SET_EX_LONG != null) {
                Object result = JEDIS_SET_EX_LONG.invoke(jedis, key, (long) expireSecond, value);
                return result != null && "OK".equals(result.toString());
            } else if (JEDIS_SET_EX_INT != null) {
                Object result = JEDIS_SET_EX_INT.invoke(jedis, key, expireSecond, value);
                return result != null && "OK".equals(result.toString());
            } else {
                throw new RuntimeException("Jedis.setex(key,expireSecond,value) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean v4v5v6_setString(Jedis jedis, String key, long expireSecond, String value) {
        String str = jedis.setex(key, expireSecond, value);
        return "OK".equals(str);
    }

    // END of setString

    // START of getExpireSecond

    public static long getExpireSecond(Jedis jedis, String key) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_getExpireSecond(jedis, key);
        } else {
            return v4v5v6_getExpireSecond(jedis, key);
        }
    }

    private static long v4v5v6_getExpireSecond(Jedis jedis, String key) {
        return jedis.ttl(key);
    }

    private static final Method JEDIS_TTL;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("ttl", String.class);
        } catch (Throwable ignored) {}
        JEDIS_TTL = m;
    }

    private static long v2v3_getExpireSecond(Jedis jedis, String key) {
        try {
            if (JEDIS_TTL != null) {
                Object result = JEDIS_TTL.invoke(jedis, key);
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                } else {
                    throw new RuntimeException("Jedis.ttl(key) return is not a number, result:" + result);
                }
            } else {
                throw new RuntimeException("Jedis.ttl(key) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of getExpireSecond

    // START of setExpire

    public static boolean setExpire(Jedis jedis, String key, int expireSecond) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_setExpire(jedis, key, expireSecond);
        } else {
            return v4v5v6_setExpire(jedis, key, expireSecond);
        }
    }

    private static boolean v4v5v6_setExpire(Jedis jedis, String key, int expireSecond) {
        jedis.expire(key, expireSecond);
        return true; // 即使key不存在，也认为是true
    }

    private static final Method JEDIS_EXPIRE_INT;
    private static final Method JEDIS_EXPIRE_LONG;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("expire", String.class, int.class);
        } catch (Throwable ignored) {}
        JEDIS_EXPIRE_INT = m;

        try {
            m = Jedis.class.getMethod("expire", String.class, long.class);
        } catch (Throwable ignored) {}
        JEDIS_EXPIRE_LONG = m;
    }

    private static boolean v2v3_setExpire(Jedis jedis, String key, int expireSecond) {
        try {
            if (JEDIS_EXPIRE_INT != null) {
                JEDIS_EXPIRE_INT.invoke(jedis, key, expireSecond);
                return true; // 即使key不存在，也认为是true
            } else if (JEDIS_EXPIRE_LONG != null) {
                JEDIS_EXPIRE_LONG.invoke(jedis, key, (long) expireSecond);
                return true; // 即使key不存在，也认为是true
            } else {
                throw new RuntimeException("Jedis.expire(key, expireSecond) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of setExpire

    // START of remove

    public static boolean remove(Jedis jedis, String key) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_remove(jedis, key);
        } else {
            return v4v5v6_remove(jedis, key);
        }
    }

    private static boolean v4v5v6_remove(Jedis jedis, String key) {
        jedis.del(key);
        return true; // 不管key是否存在，remove都认为是成功
    }

    private static final Method JEDIS_DEL;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("del", String.class);
        } catch (Throwable ignored) {}
        JEDIS_DEL = m;
    }

    private static boolean v2v3_remove(Jedis jedis, String key) {
        try {
            if (JEDIS_DEL != null) {
                JEDIS_DEL.invoke(jedis, key);
                return true; // 不管key是否存在，remove都认为是成功
            } else {
                throw new RuntimeException("Jedis.del(key) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of remove

    // START of incr

    public static long incr(Jedis jedis, String key) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_incr(jedis, key);
        } else {
            return v4v5v6_incr(jedis, key);
        }
    }

    private static long v4v5v6_incr(Jedis jedis, String key) {
        return jedis.incr(key);
    }

    private static final Method JEDIS_INCR;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("incr", String.class);
        } catch (Throwable ignored) {}
        JEDIS_INCR = m;
    }

    private static long v2v3_incr(Jedis jedis, String key) {
        try {
            if (JEDIS_INCR != null) {
                Object result = JEDIS_INCR.invoke(jedis, key);
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                } else {
                    throw new RuntimeException("Jedis.incr(key) return is not a number, result:" + result);
                }
            } else {
                throw new RuntimeException("Jedis.incr(key) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of incr

    // START of incrBy

    public static long incrBy(Jedis jedis, String key, long increment) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_incrBy(jedis, key, increment);
        } else {
            return v4v5v6_incrBy(jedis, key, increment);
        }
    }

    private static long v4v5v6_incrBy(Jedis jedis, String key, long increment) {
        return jedis.incrBy(key, increment);
    }

    private static final Method JEDIS_INCR_BY;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("incrBy", String.class, long.class);
        } catch (Throwable ignored) {}
        JEDIS_INCR_BY = m;
    }

    private static long v2v3_incrBy(Jedis jedis, String key, long increment) {
        try {
            if (JEDIS_INCR_BY != null) {
                Object result = JEDIS_INCR_BY.invoke(jedis, key, increment);
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                } else {
                    throw new RuntimeException("Jedis.incrBy(key, increment) return is not a number, result:" + result);
                }
            } else {
                throw new RuntimeException("Jedis.incrBy(key, increment) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of incrBy

    // START of decr

    public static long decr(Jedis jedis, String key) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_decr(jedis, key);
        } else {
            return v4v5v6_decr(jedis, key);
        }
    }

    private static long v4v5v6_decr(Jedis jedis, String key) {
        return jedis.decr(key);
    }

    private static final Method JEDIS_DECR;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("decr", String.class);
        } catch (Throwable ignored) {}
        JEDIS_DECR = m;
    }

    private static long v2v3_decr(Jedis jedis, String key) {
        try {
            if (JEDIS_DECR != null) {
                Object result = JEDIS_DECR.invoke(jedis, key);
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                } else {
                    throw new RuntimeException("Jedis.decr(key) return is not a number, result:" + result);
                }
            } else {
                throw new RuntimeException("Jedis.decr(key) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of decr

    // START of decrBy

    public static long decrBy(Jedis jedis, String key, long decrement) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_decrBy(jedis, key, decrement);
        } else {
            return v4v5v6_decrBy(jedis, key, decrement);
        }
    }

    private static long v4v5v6_decrBy(Jedis jedis, String key, long decrement) {
        return jedis.decrBy(key, decrement);
    }

    private static final Method JEDIS_DECR_BY;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("decrBy", String.class, long.class);
        } catch (Throwable ignored) {}
        JEDIS_DECR_BY = m;
    }

    private static long v2v3_decrBy(Jedis jedis, String key, long decrement) {
        try {
            if (JEDIS_DECR_BY != null) {
                Object result = JEDIS_DECR_BY.invoke(jedis, key, decrement);
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                } else {
                    throw new RuntimeException("Jedis.decrBy(key, decrement) return is not a number, result:" + result);
                }
            } else {
                throw new RuntimeException("Jedis.decrBy(key, decrement) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of decrBy

    // START of sadd

    public static long sadd(Jedis jedis, String key, String member) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_sadd(jedis, key, member);
        } else {
            return v4v5v6_sadd(jedis, key, member);
        }
    }

    private static long v4v5v6_sadd(Jedis jedis, String key, String member) {
        return jedis.sadd(key, member);
    }

    private static final Method JEDIS_SADD;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("sadd", String.class, String[].class);
        } catch (Throwable ignored) {}
        JEDIS_SADD = m;
    }

    private static long v2v3_sadd(Jedis jedis, String key, String member) {
        try {
            if (JEDIS_SADD != null) {
                Object result = JEDIS_SADD.invoke(jedis, key, new String[]{member});
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                } else {
                    throw new RuntimeException("Jedis.sadd(key, member) return is not a number, result:" + result);
                }
            } else {
                throw new RuntimeException("Jedis.sadd(key, member) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of sadd

    // START of hset

    public static long hset(Jedis jedis, String key, String field, String value) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_hset(jedis, key, field, value);
        } else {
            return v4v5v6_hset(jedis, key, field, value);
        }
    }

    private static long v4v5v6_hset(Jedis jedis, String key, String field, String value) {
        return jedis.hset(key, field, value);
    }

    private static final Method JEDIS_HSET;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("hset", String.class, String.class, String.class);
        } catch (Throwable ignored) {}
        JEDIS_HSET = m;
    }

    private static long v2v3_hset(Jedis jedis, String key, String field, String value) {
        try {
            if (JEDIS_HSET != null) {
                Object result = JEDIS_HSET.invoke(jedis, key, field, value);
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                } else {
                    throw new RuntimeException("Jedis.hset(key, field, value) return is not a number, result:" + result);
                }
            } else {
                throw new RuntimeException("Jedis.hset(key, field, value) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of hset

    // START of llen

    public static long llen(Jedis jedis, String key) {
        if (jedisVersion >= 2 && jedisVersion <= 3) {
            return v2v3_llen(jedis, key);
        } else {
            return v4v5v6_llen(jedis, key);
        }
    }

    private static long v4v5v6_llen(Jedis jedis, String key) {
        return jedis.llen(key);
    }

    private static final Method JEDIS_LLEN;

    static {
        Method m = null;
        try {
            m = Jedis.class.getMethod("llen", String.class);
        } catch (Throwable ignored) {}
        JEDIS_LLEN = m;
    }

    private static long v2v3_llen(Jedis jedis, String key) {
        try {
            if (JEDIS_LLEN != null) {
                Object result = JEDIS_LLEN.invoke(jedis, key);
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                } else {
                    throw new RuntimeException("Jedis.llen(key) return is not a number, result:" + result);
                }
            } else {
                throw new RuntimeException("Jedis.llen(key) not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // END of llen

}
