package com.pugwoo.wooutils;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.RedisMsg;
import com.pugwoo.wooutils.redis.RedisQueueStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ContextConfiguration(locations = {"classpath:applicationContext-context.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class TestRedisAckQueue {

    @Autowired
    private RedisHelper redisHelper;

    @Test
    public void testSendAndRecvAckOne() {
        String randomTopic = "mytopic-" + UUID.randomUUID().toString();
        String body = "msgconent" + UUID.randomUUID().toString();

        String uuid = redisHelper.send(randomTopic, body);
        // System.out.println("send msg:" + uuid);
        assert uuid != null && !uuid.isEmpty();

        RedisMsg msg = redisHelper.receive(randomTopic); // 阻塞
        assert msg != null;
        assert msg.getMsg().equals(body);

        // System.out.println("revc msg ack uuid:" + msg.getUuid() + ",content:" + msg.getMsg());
        assert redisHelper.ack("mytopic2", msg.getUuid());
    }

    @Test
    public void testRecvNackOne() {
        String randomTopic = "mytopic-" + UUID.randomUUID().toString();
        String body = "msgconent" + UUID.randomUUID().toString();

        String uuid = redisHelper.send(randomTopic, body);
        // System.out.println("send msg:" + uuid);
        assert uuid != null && !uuid.isEmpty();

        RedisMsg msg = redisHelper.receive(randomTopic); // 阻塞
        assert msg != null;
        assert msg.getMsg().equals(body);

        assert redisHelper.nack("mytopic2", msg.getUuid());

        // 再次等待接收
        msg = redisHelper.receive(randomTopic); // 阻塞
        assert msg != null;
        assert msg.getMsg().equals(body);

        // System.out.println("revc msg ack uuid:" + msg.getUuid() + ",content:" + msg.getMsg());
        assert redisHelper.ack("mytopic2", msg.getUuid());
    }
    
    @Test
    public void testSendBatch() {
        String randomTopic = "mytopic-" + UUID.randomUUID().toString();

        List<String> msgList4 = new ArrayList<>();
        msgList4.add("msgconent" + UUID.randomUUID().toString());
        msgList4.add("msgconent" + UUID.randomUUID().toString());
        msgList4.add("msgconent" + UUID.randomUUID().toString());

        List<String> uuidList4 = redisHelper.sendBatch(randomTopic, msgList4);
        assert uuidList4.size() == 3;
    
        List<String> msgList5 = new ArrayList<>();
        msgList5.add("msgconent" + UUID.randomUUID().toString());
        msgList5.add("msgconent" + UUID.randomUUID().toString());
        msgList5.add("msgconent" + UUID.randomUUID().toString());

        randomTopic = "mytopic-" + UUID.randomUUID().toString();

        List<String> uuidList5 = redisHelper.sendBatch(randomTopic, msgList5, 60);
        assert uuidList5.size() == 3;

    }

    @Test
    public void testCleanTopic() {
        assert redisHelper.removeTopic("mytopic5");
    }

    @Test
    public void testCleanTopicAndGetStatus() {
        String randomTopic = "mytopic-" + UUID.randomUUID().toString();

        String uuid = redisHelper.send(randomTopic, UUID.randomUUID().toString());
        assert uuid.length() > 0;
        uuid = redisHelper.send(randomTopic, UUID.randomUUID().toString());
        assert uuid.length() > 0;
        uuid = redisHelper.send(randomTopic, UUID.randomUUID().toString());
        assert uuid.length() > 0;

        RedisQueueStatus status = redisHelper.getQueueStatus(randomTopic);
        assert status.getPendingCount() == 3;
        assert status.getDoingCount() == 0;

        // 然后收一个消息
        RedisMsg receive = redisHelper.receive(randomTopic);
        assert receive.getUuid().length() > 0;

        status = redisHelper.getQueueStatus(randomTopic);
        assert status.getPendingCount() == 2;
        assert status.getDoingCount() == 1;

        // 然后ack一个消息
        assert redisHelper.ack(randomTopic, receive.getUuid());
        status = redisHelper.getQueueStatus(randomTopic);
        assert status.getPendingCount() == 2;
    }

}
