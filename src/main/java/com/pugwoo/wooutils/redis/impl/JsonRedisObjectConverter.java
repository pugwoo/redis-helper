package com.pugwoo.wooutils.redis.impl;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.pugwoo.wooutils.redis.IRedisObjectConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 使用json只序列化field，不序列化getter setter
 */
public class JsonRedisObjectConverter implements IRedisObjectConverter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonRedisObjectConverter.class);

	// 这里并没有使用到封装的JSON工具类，是因为这里的序列化和反序列化不依赖于getter/setter同时相对单一稳定
	private static ObjectMapper mapper;

	static {
		mapper  = new ObjectMapper();
		mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false); // 对于没有任何getter的bean序列化不抛异常
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); //属性不存在的兼容处理
		mapper.getSerializerProvider().setNullKeySerializer(new NullKeySerializer()); // 当map含有null key时，转成空字符串
	}

	private static class NullKeySerializer extends JsonSerializer<Object> {

		@Override
		public void serialize(Object nullKey, JsonGenerator jsonGenerator, SerializerProvider unused)
				throws IOException, JsonProcessingException {
			jsonGenerator.writeFieldName("");
		}

	}

	@Override
	public <T> String convertToString(T t) {
		return toJson(t);
	}

	public static <T> String toJson(T t) {
		if(t == null) {
			return null;
		}
		try {
			return mapper.writeValueAsString(t);
		} catch (JsonProcessingException e) {
			LOGGER.error("convert object to json string fail", e);
			return null;
		}
	}

	@Override
	public <T> T convertToObject(String str, Class<T> clazz) {
		return parse(str, clazz);
	}

	public static <T> T parse(String str, Class<T> clazz) {
		if(str == null || str.isEmpty()) {
			return null;
		}
		try {
			return mapper.readValue(str, clazz);
		} catch (Exception e) {
			LOGGER.error("convert json string to object fail", e);
			return null;
		}
	}

    @Override
    public <T> T convertToObject(String str, Class<T> clazz, Class<?> genericClass) {
		return parse(str, clazz, genericClass);
    }

    public static <T> T parse(String str, Class<T> clazz, Class<?> genericClass) {
		if (str == null || str.isEmpty()) {
			return null;
		}
		try {
			JavaType type = mapper.getTypeFactory()
					.constructParametricType(clazz, genericClass);
			return mapper.readValue(str, type);
		} catch (Exception e) {
			LOGGER.error("convert json string to object fail", e);
			return null;
		}
	}

    @Override
    public <T> T convertToObject(String str, Class<T> clazz, Class<?> genericClass1, Class<?> genericClass2) {
		return parse(str, clazz, genericClass1, genericClass2);
    }

    public static <T> T parse(String str, Class<T> clazz, Class<?> genericClass1, Class<?> genericClass2) {
		if (str == null || str.isEmpty()) {
			return null;
		}
		try {
			JavaType type = mapper.getTypeFactory()
					.constructParametricType(clazz, genericClass1, genericClass2);
			return mapper.readValue(str, type);
		} catch (Exception e) {
			LOGGER.error("convert json string to object fail", e);
			return null;
		}
	}
}
