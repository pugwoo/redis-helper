package com.pugwoo.redishelpertest.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.pugwoo.redishelpertest.redis.Student;
import com.pugwoo.wooutils.collect.ListUtils;
import com.pugwoo.wooutils.lang.EqualUtils;
import com.pugwoo.wooutils.redis.RedisHelper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

public abstract class TestRedisHelper {

    public abstract RedisHelper getRedisHelper();

    @Test
    public void test0() {
        assert getRedisHelper().isOk();
    }

    @Test
    public void testRename() {
        String oldKey = UUID.randomUUID().toString();
        String newKey = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        getRedisHelper().setString(oldKey, 10, value);
        assert getRedisHelper().getString(oldKey).equals(value);

        getRedisHelper().rename(oldKey, newKey);
        assert getRedisHelper().getString(oldKey) == null;
        assert getRedisHelper().getString(newKey).equals(value);
    }

    @Test
    public void testBasicGetSet() {
        String key = "mytest" + UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        getRedisHelper().setString(key, 10, value);
        String value2 = getRedisHelper().getString(key);
        assert value.equals(value2);

        long expireSecond = getRedisHelper().getExpireSecond(key);
        assert expireSecond > 5 && expireSecond <= 10;

        getRedisHelper().setExpire(key, 20);
        expireSecond = getRedisHelper().getExpireSecond(key);
        assert expireSecond > 15 && expireSecond <= 20;

        List<String> keys = new ArrayList<>();
        for(int i = 0; i < 10; i++) {
            String k = UUID.randomUUID().toString();
            keys.add(k);
            getRedisHelper().setString(k, 60, k);
        }

        List<String> strings = getRedisHelper().getStrings(keys);
        assert new EqualUtils().isEqual(keys, strings); // 有顺序的
    }

    @Test
    public void testGetSetObject() {
        Student student = new Student();
        student.setId(3L);
        student.setName("nick");
        student.setBirth(new Date());
        List<BigDecimal> scores = new ArrayList<>();
        scores.add(BigDecimal.ONE);
        scores.add(new BigDecimal(99));
        scores.add(new BigDecimal("33.333"));

        student.setScore(scores);

        getRedisHelper().setObject("just-test000", 10, student);
        Student student2 = getRedisHelper().getObject("just-test000", Student.class);

        assert new EqualUtils().isEqual(student, student2);

        List<Student> list = new ArrayList<>();
        list.add(student);
        getRedisHelper().setObject("just-test111", 10, list);
        List<Student> list2 = getRedisHelper().getObject("just-test111", List.class, Student.class);

        assert new EqualUtils().isEqual(list, list2);

        list2 = getRedisHelper().getObject("just-test111", new TypeReference<List<Student>>() {
        });
        assert new EqualUtils().isEqual(list, list2);

        List<List<Student>> list2s = getRedisHelper().getObjects(ListUtils.of("just-test111"),
                new TypeReference<List<Student>>() {
                });
        assert new EqualUtils().isEqual(list, list2s.get(0));

        Map<Integer, Student> map = new HashMap<>();
        map.put(1, student);
        getRedisHelper().setObject("just-test333", 10, map);
        Map<Integer, Student> map2 = getRedisHelper().getObject("just-test333", Map.class, Integer.class, Student.class);

        assert new EqualUtils().isEqual(map, map2);

        map2 = getRedisHelper().getObject("just-test333", new TypeReference<Map<Integer, Student>>() {
        });
        assert new EqualUtils().isEqual(map, map2);

        List<Map<Integer, Student>> map2s = getRedisHelper().getObjects(ListUtils.of("just-test333"), new TypeReference<Map<Integer, Student>>() {
        });
        assert new EqualUtils().isEqual(map, map2s.get(0));

    }

    @Test
    public void testDelete() {
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();

        getRedisHelper().setString(key, 60, value);
        assert getRedisHelper().getString(key).equals(value);

        getRedisHelper().remove(key);
        assert getRedisHelper().getString(key) == null;

        getRedisHelper().setString(key, 60, value);
        assert getRedisHelper().getString(key).equals(value);

        getRedisHelper().remove(key, "11111"); // 应该删除不掉
        assert getRedisHelper().getString(key).equals(value);

        getRedisHelper().remove(key, value); // 应该删除掉
        assert getRedisHelper().getString(key) == null;
    }

    @Test
    public void testSetIfNotExist() {
        String key = "mytest" + UUID.randomUUID().toString();
        boolean result1 = getRedisHelper().setStringIfNotExist(key, 60, "you1");
        boolean result2 = getRedisHelper().setStringIfNotExist(key, 60, "you2");
        boolean result3 = getRedisHelper().setStringIfNotExist(key, 60, "you3");
        assert result1;
        assert !result2;
        assert !result3;

        assert getRedisHelper().getString(key).equals("you1");
    }

    @Test
    public void testCAS() {
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        // value = null;

        getRedisHelper().setString(key, 60, value);

        assert getRedisHelper().compareAndSet(key, "111", value, 30);

        assert "111".equals(getRedisHelper().getString(key));
        assert getRedisHelper().getExpireSecond(key) > 25 && getRedisHelper().getExpireSecond(key) <= 30;

        assert !getRedisHelper().compareAndSet(key, "111", value, null);
    }

    @Test
    public void testPipeline() {
        List<Object> objs = getRedisHelper().executePipeline(pipeline -> {
            pipeline.set("hello", "world");
            pipeline.get("hello");

        });

        assert objs.size() == 2;
        assert objs.get(0).equals("OK");
        assert objs.get(1).equals("world");

        getRedisHelper().remove("hello");
    }

    @Test
    public void testTransaction() {
        String key = UUID.randomUUID().toString();
        List<Object> objs = getRedisHelper().executeTransaction(transaction -> {
            transaction.set(key, "hello");
            transaction.get(key);
        }, key);

        assert objs.size() == 2;
        assert objs.get(0).equals("OK");
        assert objs.get(1).equals("hello");

        getRedisHelper().remove(key);
    }

}
