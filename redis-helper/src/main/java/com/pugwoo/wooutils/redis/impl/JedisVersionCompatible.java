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
