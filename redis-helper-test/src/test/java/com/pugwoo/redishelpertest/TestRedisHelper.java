package com.pugwoo.redishelpertest;

import com.pugwoo.redishelpertest.redis.Student;
import com.pugwoo.wooutils.lang.EqualUtils;
import com.pugwoo.wooutils.redis.RedisHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.*;

@SpringBootTest
public class TestRedisHelper {
	
    @Autowired
	private RedisHelper redisHelper;

	@Test
	public void test0() {
		assert redisHelper.isOk();
	}

	@Test
	public void testRename() {
		String oldKey = UUID.randomUUID().toString();
		String newKey = UUID.randomUUID().toString();
		String value = UUID.randomUUID().toString();
		redisHelper.setString(oldKey, 10, value);
		assert redisHelper.getString(oldKey).equals(value);

		redisHelper.rename(oldKey, newKey);
		assert redisHelper.getString(oldKey) == null;
		assert redisHelper.getString(newKey).equals(value);
	}
	
	@Test
	public void testBasicGetSet() {
		String key = "mytest" + UUID.randomUUID().toString();
		String value = UUID.randomUUID().toString();
		redisHelper.setString(key, 10, value);
		String value2 = redisHelper.getString(key);
	    assert value.equals(value2);

		long expireSecond = redisHelper.getExpireSecond(key);
		assert expireSecond > 5 && expireSecond <= 10;

		redisHelper.setExpire(key, 20);
		expireSecond = redisHelper.getExpireSecond(key);
		assert expireSecond > 15 && expireSecond <= 20;

		List<String> keys = new ArrayList<>();
		for(int i = 0; i < 10; i++) {
			String k = UUID.randomUUID().toString();
			keys.add(k);
			redisHelper.setString(k, 60, k);
		}

		List<String> strings = redisHelper.getStrings(keys);
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

		redisHelper.setObject("just-test000", 10, student);
		Student student2 = redisHelper.getObject("just-test000", Student.class);

		assert new EqualUtils().isEqual(student, student2);

		List<Student> list = new ArrayList<>();
		list.add(student);
		redisHelper.setObject("just-test111", 10, list);
		List<Student> list2 = redisHelper.getObject("just-test111", List.class, Student.class);

		assert new EqualUtils().isEqual(list, list2);

		Map<Integer, Student> map = new HashMap<>();
		map.put(1, student);
		redisHelper.setObject("just-test333", 10, map);
		Map<Integer, Student> map2 = redisHelper.getObject("just-test333", Map.class, Integer.class, Student.class);

		assert new EqualUtils().isEqual(map, map2);

	}

    @Test
	public void testDelete() {
		String key = UUID.randomUUID().toString();
		String value = UUID.randomUUID().toString();

		redisHelper.setString(key, 60, value);
		assert redisHelper.getString(key).equals(value);

		redisHelper.remove(key);
		assert redisHelper.getString(key) == null;

		redisHelper.setString(key, 60, value);
		assert redisHelper.getString(key).equals(value);

		redisHelper.remove(key, "11111"); // 应该删除不掉
		assert redisHelper.getString(key).equals(value);

		redisHelper.remove(key, value); // 应该删除掉
		assert redisHelper.getString(key) == null;
	}
	
	@Test
	public void testSetIfNotExist() {
		String key = "mytest" + UUID.randomUUID().toString();
		boolean result1 = redisHelper.setStringIfNotExist(key, 60, "you1");
		boolean result2 = redisHelper.setStringIfNotExist(key, 60, "you2");
		boolean result3 = redisHelper.setStringIfNotExist(key, 60, "you3");
		assert result1;
		assert !result2;
		assert !result3;

		assert redisHelper.getString(key).equals("you1");
	}

    @Test
	public void testCAS() {
		String key = UUID.randomUUID().toString();
		String value = UUID.randomUUID().toString();
		// value = null;

		redisHelper.setString(key, 60, value);

		assert redisHelper.compareAndSet(key, "111", value, 30);

		assert "111".equals(redisHelper.getString(key));
		assert redisHelper.getExpireSecond(key) > 25 && redisHelper.getExpireSecond(key) <= 30;
	
		assert !redisHelper.compareAndSet(key, "111", value, null);
	}

	@Test
	public void testPipeline() {
		List<Object> objs = redisHelper.executePipeline(pipeline -> {
			pipeline.set("hello", "world");
			pipeline.get("hello");

		});

		assert objs.size() == 2;
		assert objs.get(0).equals("OK");
		assert objs.get(1).equals("world");

		redisHelper.remove("hello");
	}
	
	@Test
	public void testTransaction() {
		String key = UUID.randomUUID().toString();
		List<Object> objs = redisHelper.executeTransaction(transaction -> {
			transaction.set(key, "hello");
			transaction.get(key);
		}, key);

		assert objs.size() == 2;
		assert objs.get(0).equals("OK");
		assert objs.get(1).equals("hello");

		redisHelper.remove(key);
	}

}
