package com.pugwoo.wooutils.redis.impl;

import com.pugwoo.wooutils.redis.exception.NoJedisConnectionException;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * getString 必须区分“key不存在（返回null）”与“Redis连接/命令异常（抛异常）”，
 * 否则分布式锁等调用方会把网络异常误判为key不存在。
 */
public class RedisHelperImplGetStringTest {

    @Test
    public void commandFailureThrowsInsteadOfReturningNull() {
        RedisHelperImpl helper = helperReturning(new StubJedis(new JedisConnectionException("down")));
        assertThrows(JedisConnectionException.class, () -> helper.getString("some-key"));
    }

    @Test
    public void missingKeyStillReturnsNull() {
        RedisHelperImpl helper = helperReturning(new StubJedis(null));
        assertNull(helper.getString("missing-key"));
    }

    @Test
    public void existingKeyReturnsValue() {
        RedisHelperImpl helper = helperReturning(new StubJedis("value".getBytes()));
        assertEquals("value", helper.getString("some-key"));
    }

    @Test
    public void getObjectPropagatesRedisExceptionInsteadOfReturningNull() {
        RedisHelperImpl helper = helperReturning(new StubJedis(new JedisConnectionException("down")));
        assertThrows(JedisConnectionException.class, () -> helper.getObject("some-key", String.class));
    }

    @Test
    public void unconfiguredRedisThrowsNoJedisConnectionInsteadOfNullPointer() {
        // host未配置时没有连接池，getJedisConnection返回null，不应以NPE或null掩盖
        RedisHelperImpl helper = new RedisHelperImpl();
        assertThrows(NoJedisConnectionException.class, () -> helper.getString("some-key"));
    }

    private static RedisHelperImpl helperReturning(Jedis jedis) {
        return new RedisHelperImpl() {
            @Override
            public <R> R execute(Function<Jedis, R> jedisToFunc) {
                return jedisToFunc.apply(jedis);
            }
        };
    }

    /**只桩住sendCommand的Jedis，不建立真实连接。result为RuntimeException时抛出它。*/
    private static final class StubJedis extends Jedis {
        private final Object result;

        private StubJedis(Object result) {
            super();
            this.result = result;
        }

        @Override
        public Object sendCommand(ProtocolCommand command, String... args) {
            if (result instanceof RuntimeException) {
                throw (RuntimeException) result;
            }
            return result;
        }
    }
}
