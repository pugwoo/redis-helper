package com.pugwoo.wooutils.redis;

import java.io.Serializable;

/**
 * Redis单位时间限制次数，所需要的参数
 * 
 * @author pugwoo
 */
public class RedisLimitParam implements Serializable {

	private static final long serialVersionUID = 3463781723698913384L;

	/** redis的key的命名空间，用于区分不同的业务 */
	private String namespace;

	/** 要控制的时间范围 */
	private RedisLimitPeriodEnum limitPeriod;

	/** 单位时间内要限制的次数 */
	private int limitCount;

	public RedisLimitParam() {
	}

	public RedisLimitParam(String namespace, RedisLimitPeriodEnum limitPeriod, int limitCount) {
		this.namespace = namespace;
		this.limitPeriod = limitPeriod;
		this.limitCount = limitCount;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public RedisLimitPeriodEnum getLimitPeriod() {
		return limitPeriod;
	}

	public void setLimitPeriod(RedisLimitPeriodEnum limitPeriod) {
		this.limitPeriod = limitPeriod;
	}

	public int getLimitCount() {
		return limitCount;
	}

	public void setLimitCount(int limitCount) {
		this.limitCount = limitCount;
	}

}
