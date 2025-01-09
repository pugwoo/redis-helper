package com.pugwoo.wooutils.redis.impl;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pugwoo.wooutils.redis.IRedisObjectConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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

		mapper.registerModule(new JavaTimeModule()); // 解析LocalDate等
	}

	private static class NullKeySerializer extends JsonSerializer<Object> {

		@Override
		public void serialize(Object nullKey, JsonGenerator jsonGenerator, SerializerProvider unused)
				throws IOException {
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
    public <T> T convertToObject(String str, Class<T> clazz, Class<?>... genericClasses) {
		return parse(str, clazz, genericClasses);
    }

    public static <T> T parse(String str, Class<T> clazz, Class<?>... genericClasses) {
		if (str == null || str.isEmpty()) { // 这里不要str.trim()，也就是空白的字符串可能是有用的字符串
			return null;
		}
		try {
			if (genericClasses == null || genericClasses.length == 0) {
				return mapper.readValue(str, clazz);
			} else {
				JavaType type = mapper.getTypeFactory().constructParametricType(clazz, genericClasses);
				return mapper.readValue(str, type);
			}
		} catch (Exception e) {
			LOGGER.error("convert json string to object fail", e);
			return null;
		}
	}

	@Override
	public <T> T convertToObject(String str, TypeReference<T> typeReference) {
		return parse(str, typeReference);
	}

	public static <T> T parse(String str, TypeReference<T> typeReference) {
		if (str == null || str.isEmpty()) { // 这里不要str.trim()，也就是空白的字符串可能是有用的字符串
			return null;
		}
		try {
			if (typeReference == null) {
				throw new RuntimeException("typeReference is null");
			} else {
				return mapper.readValue(str, typeReference);
			}
		} catch (Exception e) {
			LOGGER.error("convert json string to object fail", e);
			return null;
		}
	}

	public static Object parse(String json, ParameterizedType type) {
		JavaType javaType = toJavaType(type);
		try {
			return mapper.readValue(json, javaType);
		} catch (Exception e) {
			LOGGER.error("convert json string to object fail", e);
			return null;
		}
	}

	private static JavaType toJavaType(ParameterizedType type) {
		TypeFactory typeFactory = mapper.getTypeFactory();

		Type rawType = type.getRawType();
		Type[] actualTypeArguments = type.getActualTypeArguments();

		JavaType[] javaTypes = new JavaType[actualTypeArguments.length];
		for (int i = 0; i < actualTypeArguments.length; i++) {
			if (actualTypeArguments[i] instanceof Class) {
				javaTypes[i] = typeFactory.constructType(actualTypeArguments[i]);
			} else if (actualTypeArguments[i] instanceof ParameterizedType) {
				javaTypes[i] = toJavaType((ParameterizedType) actualTypeArguments[i]);
			} else {
				LOGGER.error("unknown actualTypeArguments type:{} in type:{}",
						actualTypeArguments[i], type);
			}
		}

		return typeFactory.constructParametricType((Class<?>) rawType, javaTypes);
	}
}
