package com.pugwoo.redishelpertest.common;

import com.pugwoo.wooutils.redis.RedisHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis Pub/Sub 发布订阅功能测试
 */
public abstract class RedisPubSubTest {

    public abstract RedisHelper getRedisHelper();

    /**
     * 测试基本的发布订阅功能
     */
    @Test
    public void testBasicPubSub() throws Exception {
        String channel = "test-channel-" + UUID.randomUUID();
        String message = "Hello, Redis Pub/Sub! " + UUID.randomUUID();

        // 在独立线程中订阅消息
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread subscriberThread = new Thread(() -> {
            String msg = getRedisHelper().subscribe(channel);
            receivedMessage.set(msg);
            latch.countDown();
        });
        subscriberThread.start();

        // 等待订阅者准备好
        Thread.sleep(500);

        // 发布消息
        Long subscribers = getRedisHelper().publish(channel, message);
        System.out.println("Published message to " + subscribers + " subscriber(s)");

        // 等待接收消息
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assert received : "Timeout waiting for message";
        assert message.equals(receivedMessage.get()) : "Message mismatch";

        System.out.println("testBasicPubSub passed, received: " + receivedMessage.get());
    }

    /**
     * 测试多个订阅者
     */
    @Test
    public void testMultipleSubscribers() throws Exception {
        String channel = "test-channel-multi-" + UUID.randomUUID();
        String message = "Broadcast message " + UUID.randomUUID();

        int subscriberCount = 3;
        List<AtomicReference<String>> receivedMessages = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(subscriberCount);

        // 启动多个订阅者
        for (int i = 0; i < subscriberCount; i++) {
            AtomicReference<String> receivedMessage = new AtomicReference<>();
            receivedMessages.add(receivedMessage);

            Thread subscriberThread = new Thread(() -> {
                String msg = getRedisHelper().subscribe(channel);
                receivedMessage.set(msg);
                latch.countDown();
            });
            subscriberThread.start();
        }

        // 等待所有订阅者准备好
        Thread.sleep(1000);

        // 发布消息
        Long subscribers = getRedisHelper().publish(channel, message);
        System.out.println("Published to " + subscribers + " subscribers");
        assert subscribers == subscriberCount : "Expected " + subscriberCount + " subscribers, got " + subscribers;

        // 等待所有订阅者接收消息
        boolean allReceived = latch.await(5, TimeUnit.SECONDS);
        assert allReceived : "Timeout waiting for all subscribers";

        // 验证所有订阅者都收到了消息
        for (AtomicReference<String> receivedMessage : receivedMessages) {
            assert message.equals(receivedMessage.get()) : "Message mismatch";
        }

        System.out.println("testMultipleSubscribers passed");
    }

    /**
     * 测试循环订阅
     */
    @Test
    public void testLoopSubscribe() throws Exception {
        String channel = "test-channel-loop-" + UUID.randomUUID();
        int messageCount = 3;
        List<String> sentMessages = new ArrayList<>();

        // 准备要发送的消息
        for (int i = 0; i < messageCount; i++) {
            sentMessages.add("Message-" + i + "-" + UUID.randomUUID());
        }

        List<String> receivedMessages = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicBoolean stopFlag = new AtomicBoolean(false);

        // 在独立线程中循环订阅
        Thread subscriberThread = new Thread(() -> {
            while (!stopFlag.get() && receivedMessages.size() < messageCount) {
                try {
                    String msg = getRedisHelper().subscribe(channel);
                    if (msg != null) {
                        receivedMessages.add(msg);
                        latch.countDown();
                        System.out.println("Received: " + msg);
                    }
                } catch (Exception e) {
                    System.err.println("Subscribe error: " + e.getMessage());
                }
            }
        });
        subscriberThread.start();

        // 等待订阅者准备好
        Thread.sleep(500);

        // 依次发布多条消息
        for (String message : sentMessages) {
            Thread.sleep(200); // 间隔发送
            Long subscribers = getRedisHelper().publish(channel, message);
            System.out.println("Published: " + message + " to " + subscribers + " subscriber(s)");
        }

        // 等待所有消息被接收
        boolean allReceived = latch.await(10, TimeUnit.SECONDS);
        stopFlag.set(true);

        assert allReceived : "Timeout waiting for all messages";
        assert receivedMessages.size() == messageCount : "Expected " + messageCount + " messages, got " + receivedMessages.size();

        // 验证消息内容
        for (int i = 0; i < messageCount; i++) {
            assert sentMessages.get(i).equals(receivedMessages.get(i)) : "Message " + i + " mismatch";
        }

        System.out.println("testLoopSubscribe passed, received " + receivedMessages.size() + " messages");
    }
}

