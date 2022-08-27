package com.pugwoo.wooutils.redis;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 对象转换接口
 * @author nick
 */
public interface IRedisObjectConverter {

	/**
	 * 将对象转换成字符串，【注意】需要自行处理null值的情况
	 *
	 * @param t 需要转换成json的对象
	 * @return
	 */
	<T> String convertToString(T t);
	
	/**
	 * 将字符串转换成对象，【注意】需要自行处理str为null值的情况
	 *
	 * @param str json字符串
	 * @param clazz 转换成的类
	 * @param genericClasses 泛型，支持多个泛型
	 */
	<T> T convertToObject(String str, Class<T> clazz, Class<?>... genericClasses);

	/**
	 * 将字符串转换成对象，【注意】需要自行处理str为null值的情况
	 *
	 * @param str json字符串
	 * @param clazz 转换成的类
	 * @param typeReference 泛型描述信息
	 */
	<T> T convertToObject(String str, Class<T> clazz, TypeReference<T> typeReference);
}
