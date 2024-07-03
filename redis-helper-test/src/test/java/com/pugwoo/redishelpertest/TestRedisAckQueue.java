package com.pugwoo.redishelpertest;

import com.pugwoo.wooutils.redis.RedisHelper;
import com.pugwoo.wooutils.redis.RedisMsg;
import com.pugwoo.wooutils.redis.RedisQueueStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
public class TestRedisAckQueue {

    @Autowired
    private RedisHelper redisHelper;

    @Test
    public void testSendAndRecvAckOne() {
        String randomTopic = "mytopic-" + UUID.randomUUID().toString();
        String body = "msgconent" + UUID.randomUUID().toString();

        String uuid = redisHelper.send(randomTopic, body);
        System.out.println("send msg:" + uuid);
        assert uuid != null && !uuid.isEmpty();

        RedisMsg msg = redisHelper.receive(randomTopic); // 阻塞
        assert msg != null;
        assert msg.getMsg().equals(body);

        assert redisHelper.ack(randomTopic, msg.getUuid());
        System.out.println("revc msg ack uuid:" + msg.getUuid() + ",content:" + msg.getMsg());
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

        assert redisHelper.nack(randomTopic, msg.getUuid());

        // 再次等待接收
        msg = redisHelper.receive(randomTopic); // 阻塞
        assert msg != null;
        assert msg.getMsg().equals(body);

        // System.out.println("revc msg ack uuid:" + msg.getUuid() + ",content:" + msg.getMsg());
        assert redisHelper.ack(randomTopic, msg.getUuid());
    }

    @Test
    public void testTimeout() {
        String randomTopic = "mytopic-" + UUID.randomUUID();
        String body = "msgconent" + UUID.randomUUID();

        String uuid = redisHelper.send(randomTopic, body, 10);

        // 先消费一个，但是不ack也不nack
        RedisMsg msg = redisHelper.receive(randomTopic); // 阻塞
        assert msg != null;
        assert msg.getMsg().equals(body);

        // 再次消费，应该可以消费到上面那个没有ack的消息
        long t1 = System.currentTimeMillis();
        msg = redisHelper.receive(randomTopic); // 阻塞
        long t2 = System.currentTimeMillis();
        assert msg != null;
        assert msg.getUuid().equals(uuid);
        assert msg.getMsg().equals(body);

        System.out.println("cost:" + (t2 - t1) + "ms");
        assert t2 - t1 >= 10000 && t2 - t1 <= 31000; // 特别说明：因为清理seq的线程有分布式锁，过期30秒，所以如果上一个锁还没释放且程序挂了，最长会等待30秒

        // 最后ack消息，让队列清理掉
        assert redisHelper.ack(randomTopic, msg.getUuid());
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

        // 接收消息
        RedisMsg msg = redisHelper.receive(randomTopic);
        assert msg != null;
        assert uuidList4.contains(msg.getUuid());
        redisHelper.ack(randomTopic, msg.getUuid());
        uuidList4.remove(msg.getUuid());

        msg = redisHelper.receive(randomTopic);
        assert msg != null;
        assert uuidList4.contains(msg.getUuid());
        redisHelper.ack(randomTopic, msg.getUuid());
        uuidList4.remove(msg.getUuid());

        msg = redisHelper.receive(randomTopic);
        assert msg != null;
        assert uuidList4.contains(msg.getUuid());
        redisHelper.ack(randomTopic, msg.getUuid());
        uuidList4.remove(msg.getUuid());

        assert uuidList4.size() == 0;

        List<String> msgList5 = new ArrayList<>();
        msgList5.add("msgconent" + UUID.randomUUID().toString());
        msgList5.add("msgconent" + UUID.randomUUID().toString());
        msgList5.add("msgconent" + UUID.randomUUID().toString());

        randomTopic = "mytopic-" + UUID.randomUUID().toString();

        List<String> uuidList5 = redisHelper.sendBatch(randomTopic, msgList5, 60);
        assert uuidList5.size() == 3;

        // 接收消息
        msg = redisHelper.receive(randomTopic);
        assert msg != null;
        assert uuidList5.contains(msg.getUuid());
        redisHelper.ack(randomTopic, msg.getUuid());
        uuidList5.remove(msg.getUuid());

        msg = redisHelper.receive(randomTopic);
        assert msg != null;
        assert uuidList5.contains(msg.getUuid());
        redisHelper.ack(randomTopic, msg.getUuid());
        uuidList5.remove(msg.getUuid());

        msg = redisHelper.receive(randomTopic);
        assert msg != null;
        assert uuidList5.contains(msg.getUuid());
        redisHelper.ack(randomTopic, msg.getUuid());
        uuidList5.remove(msg.getUuid());

        assert uuidList5.size() == 0;
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

        // 接收剩下的2个消息
        RedisMsg msg1 = redisHelper.receive(randomTopic);
        RedisMsg msg2 = redisHelper.receive(randomTopic);

        status = redisHelper.getQueueStatus(randomTopic);
        assert status.getPendingCount() == 0;
        assert status.getDoingCount() == 2;

        // ack剩下的2个消息
        redisHelper.ack(randomTopic, msg1.getUuid());
        redisHelper.ack(randomTopic, msg2.getUuid());

        status = redisHelper.getQueueStatus(randomTopic);
        assert status.getPendingCount() == 0;
        assert status.getDoingCount() == 0;

    }

}
