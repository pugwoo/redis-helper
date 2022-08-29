package com.pugwoo.wooutils.utils;

import java.lang.reflect.Method;

/**
 * @author sapluk <br>
 *
 */
public class ClassUtils {
    
    /**
     * 获取方法的签名 含全限定类名
     */
    public static String getMethodSignatureWithClassName(Method method) {
        String clazzName = method.getDeclaringClass().getName();
        return clazzName + "." + getMethodSignature(method);
    }
    
    /**
     * 获取方法的签名
     */
    public static String getMethodSignature(Method method) {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        return methodName + ":" + toString(parameterTypes);
    }
    
    /**
     * 将参数类型转换成字符串，各类型按","分开
     */
    private static String toString(Class<?>[] parameterTypes) {
        if(parameterTypes == null || parameterTypes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for(Class<?> clazz : parameterTypes) {
            sb.append(clazz.getName()).append(",");
        }
        return sb.substring(0, sb.length() - 1);
    }
}
