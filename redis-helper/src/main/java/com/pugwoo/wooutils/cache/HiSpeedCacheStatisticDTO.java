package com.pugwoo.wooutils.cache;

/**
 * 高速缓存的统计信息
 */
public class HiSpeedCacheStatisticDTO {

    /**
     * 当前缓存中数据量
     */
    private Integer cacheDataCount;

    public static HiSpeedCacheStatisticDTO defaultValue() {
        HiSpeedCacheStatisticDTO stat = new HiSpeedCacheStatisticDTO();
        stat.setCacheDataCount(0);
        return stat;
    }

    public Integer getCacheDataCount() {
        return cacheDataCount;
    }

    public void setCacheDataCount(Integer cacheDataCount) {
        this.cacheDataCount = cacheDataCount;
    }

}
