package com.pugwoo.wooutils.redis;

import java.util.Objects;

/**
 * 频率控制周期
 * @author nick
 */
public enum RedisLimitPeriodEnum {

	/**每秒*/
	SECOND("LIMIT_PERIOD_SECOND", "每秒", 1 + 60),
	/**每10秒*/
	TEN_SECOND("LIMIT_PERIOD_TEN_SECOND", "每10秒", 10 + 60),
	/**每分钟*/
	MINUTE("LIMIT_PERIOD_MINUTE", "每分钟", 60 + 60),
	/**每小时*/
	HOUR("LIMIT_PERIOD_HOUR", "每小时", 3600 + 60),
	/**每自然日*/
	DAY("LIMIT_PERIOD_DAY", "每自然日", 24 * 3600 + 60),
	/**每周(从周日开始)*/
	WEEK_START_SUNDAY("LIMIT_PERIOD_WEEK_START_SUNDAY", "每周(从周日开始)", 7 * 24 * 3600 + 60),
	/**每周(从周一开始)*/
	WEEK_START_MONDAY("LIMIT_PERIOD_WEEK", "每周(从周一开始)", 7 * 24 * 3600 + 60),
	/**每自然月*/
	MONTH("LIMIT_PERIOD_MONTH", "每自然月", 31 * 24 * 3600 + 60),
	/**每年*/
	YEAR("LIMIT_PERIOD_YEAR", "每年", 366 * 24 * 3600 + 60),
	/**永久*/
	PERMANENT("LIMIT_PERIOD_PERMANENT", "永久", -1);
	
	private String code;
	
	private String name;
	
	/**
	 * key超时时间：设置为key本身最长有效期外+60秒。主要考虑到机器间时间差异大概率不会超过一分钟
	 * 同时也方便redis清理数据，不至于留存太多。当值为-1时，表示不过期。
	 */
	private int expireSecond;
	
	private RedisLimitPeriodEnum(String code, String name, int expireSecond) {
		this.code = code;
		this.name = name;
		this.expireSecond = expireSecond;
	}
	
	public static RedisLimitPeriodEnum getByCode(String code) {
		for(RedisLimitPeriodEnum e : RedisLimitPeriodEnum.values()) {
			if(Objects.equals(code, e.getCode())) {
				return e;
			}
		}
		return null;
	}
	
	public String getCode() {
		return code;
	}
	
	public void setCode(String code) {
		this.code = code;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public int getExpireSecond() {
		return expireSecond;
	}

	public void setExpireSecond(int expireSecond) {
		this.expireSecond = expireSecond;
	}
	
}
