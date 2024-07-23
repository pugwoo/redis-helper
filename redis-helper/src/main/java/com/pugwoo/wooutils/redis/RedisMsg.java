package com.pugwoo.wooutils.redis;

/**
 * redis消息队列的消息体
 */
public class RedisMsg {

    /**消息uuid*/
    private String uuid;

    /**消息正文*/
    private String msg;

    /**发送消息时间戳，毫秒*/
    private long sendTime;

    /**消费时间戳，毫秒；未被消费时，其值为null*/
    private Long recvTime;

    /**ack确认超时时间，秒*/
    private int ackTimeout;

    /**消费次数*/
    private Integer consumeCount;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public Long getRecvTime() {
        return recvTime;
    }

    public void setRecvTime(Long recvTime) {
        this.recvTime = recvTime;
    }

    public int getAckTimeout() {
        return ackTimeout;
    }

    public void setAckTimeout(int ackTimeout) {
        this.ackTimeout = ackTimeout;
    }

    public Integer getConsumeCount() {
        return consumeCount;
    }

    public void setConsumeCount(Integer consumeCount) {
        this.consumeCount = consumeCount;
    }
}
