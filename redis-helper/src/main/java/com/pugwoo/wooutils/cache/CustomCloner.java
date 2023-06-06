package com.pugwoo.wooutils.cache;

/**
 * 自定义克隆对象的接口，如果需要自定义克隆对象，实现此接口即可
 */
public interface CustomCloner {

    /**
     * 由实现方自行实现克隆逻辑，请确保返回值和入参obj的类型完全一致，包括泛型。
     * 组件本身不会检查类型，请自行保证。
     * @param obj 要clone的对象
     * @return 克隆后的对象
     */
    <T> T clone(T obj);

}
