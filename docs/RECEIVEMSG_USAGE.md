# @ReceiveMsg 注解使用说明

## 功能介绍

`@ReceiveMsg` 注解用于自动接收和处理 Redis 消息队列中的消息。该注解会启动独立的线程来阻塞等待消息，当收到消息后，会将 `RedisMsg` 实例传入方法的参数中。当处理方法处理完成后，自动调用 `ack`；如果抛出异常，则调用 `nack`。

功能等价于手工调用 `redisHelper.receive()` 方法，但更加简洁和自动化。

## 核心特性

- **自动消息接收**：启动独立线程阻塞等待消息
- **自动 ACK/NACK**：处理成功自动 ack，异常自动 nack
- **独立线程池**：使用专门的线程池管理消费者线程
- **按需创建线程**：每个消费者自动获得独立线程，不会在队列中等待
- **多线程消费**：支持为单个 topic 配置多个消费者线程
- **多 topic 支持**：一个方法可以监听多个 topic

## 注解参数

- **topic** (必须): 消息的 topic，即 Redis 的 key
- **ackTimeoutSec** (可选): ack 确认超时的秒数，默认值为 -1（使用发送方设置的超时时间）
- **consumerThreads** (可选): 消费者线程数量，默认为 1

## 使用示例

### 1. 基本用法

```java
@Service
public class OrderService {

    @ReceiveMsg(topic = "order-topic")
    public void handleOrder(RedisMsg msg) {
        String orderInfo = msg.getMsg();
        // 处理订单逻辑
        System.out.println("Processing order: " + orderInfo);
    }
}
```

当有消息发送到 `order-topic` 时，`handleOrder` 方法会自动被调用。

### 2. 自定义 ACK 超时时间

```java
@ReceiveMsg(topic = "slow-task-topic", ackTimeoutSec = 300)
public void handleSlowTask(RedisMsg msg) {
    // 处理耗时任务，最多 5 分钟
    String taskData = msg.getMsg();
    // 业务逻辑...
}
```

### 3. 多线程并发消费

```java
@ReceiveMsg(topic = "high-volume-topic", consumerThreads = 5)
public void handleHighVolumeMessages(RedisMsg msg) {
    // 5 个线程并发处理消息
    String data = msg.getMsg();
    // 业务逻辑...
}
```

**注意**：多线程消费时，消息的顺序无法保证。

### 4. 监听多个 Topic

使用 `@ReceiveMsgs` 注解可以让一个方法监听多个 topic：

```java
@ReceiveMsgs({
    @ReceiveMsg(topic = "topic1"),
    @ReceiveMsg(topic = "topic2", ackTimeoutSec = 60)
})
public void handleMultipleTopics(RedisMsg msg) {
    String message = msg.getMsg();
    // 处理来自 topic1 或 topic2 的消息
}
```

### 5. 异常处理（自动 NACK）

```java
@ReceiveMsg(topic = "order-topic")
public void handleOrder(RedisMsg msg) {
    String orderInfo = msg.getMsg();

    if (!isValidOrder(orderInfo)) {
        // 抛出异常会自动调用 nack，消息会重新投递
        throw new IllegalArgumentException("Invalid order: " + orderInfo);
    }

    // 正常处理，方法返回后自动调用 ack
    processOrder(orderInfo);
}
```

### 6. 访问消息元数据

```java
@ReceiveMsg(topic = "order-topic")
public void handleOrder(RedisMsg msg) {
    String content = msg.getMsg();           // 消息内容
    String uuid = msg.getUuid();             // 消息 UUID
    long sendTime = msg.getSendTime();       // 发送时间
    Long recvTime = msg.getRecvTime();       // 接收时间
    int ackTimeout = msg.getAckTimeout();    // ACK 超时时间
    Integer consumeCount = msg.getConsumeCount(); // 消费次数

    // 业务逻辑...
}
```

## 配置

在 Spring 配置类中注册 `ReceiveMsgProcessor`：

```java
@Configuration
public class RedisHelperConfiguration {

    @Bean
    public ReceiveMsgProcessor receiveMsgProcessor() {
        return new ReceiveMsgProcessor();
    }

    // 其他配置...
}
```

## 线程池配置

`ReceiveMsgProcessor` 使用独立的线程池来管理消费者线程：

- **核心线程数**：0（按需创建）
- **最大线程数**：默认200，可自行指定
- **空闲线程存活时间**：60 秒
- **队列类型**：SynchronousQueue（直接提交，不缓存任务）
- **拒绝策略**：AbortPolicy（抛出异常）

**设计说明**：
- 每个消费者线程会永久阻塞在 `receive()` 上等待消息，因此必须为每个消费者分配独立线程
- 使用 `SynchronousQueue` 确保每个消费者任务立即获得线程，不会在队列中等待
- 空闲线程60秒后自动回收，避免资源浪费
- 理论上支持无限数量的消费者（受限于系统资源）

## 方法签名要求

使用 `@ReceiveMsg` 注解的方法必须满足以下要求：

1. 方法必须有且仅有一个参数
2. 参数类型必须是 `RedisMsg`
3. 方法可以有任意返回值（返回值会被忽略）
4. 方法可以抛出任何异常（异常会触发 nack）

**正确示例**：
```java
@ReceiveMsg(topic = "my-topic")
public void handleMessage(RedisMsg msg) { }

@ReceiveMsg(topic = "my-topic")
public String processMessage(RedisMsg msg) { return "ok"; }
```

**错误示例**：
```java
// 错误：没有参数
@ReceiveMsg(topic = "my-topic")
public void handleMessage() { }

// 错误：参数类型不对
@ReceiveMsg(topic = "my-topic")
public void handleMessage(String msg) { }

// 错误：参数数量不对
@ReceiveMsg(topic = "my-topic")
public void handleMessage(RedisMsg msg, String extra) { }
```

## 注意事项

1. **服务类必须是 Spring Bean**：使用 `@Service`、`@Component` 等注解标注，或通过 `@Bean` 方法注册
2. **自动启动**：应用启动后，消费者线程会自动启动并开始监听
3. **阻塞等待**：消费者线程会永久阻塞等待消息
4. **异常重试**：抛出异常会触发 nack，消息会在超时后重新投递
5. **优雅关闭**：应用关闭时，会等待正在处理的消息完成（最多 30 秒）
6. **线程安全**：如果使用多线程消费，需要确保处理逻辑是线程安全的

## 完整示例

```java
@Service
public class MessageConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageConsumerService.class);

    @Autowired
    private OrderService orderService;

    /**
     * 处理订单消息
     */
    @ReceiveMsg(topic = "order-created", ackTimeoutSec = 60)
    public void handleOrderCreated(RedisMsg msg) {
        try {
            String orderJson = msg.getMsg();
            LOGGER.info("Received order: {}", orderJson);

            // 解析订单
            Order order = parseOrder(orderJson);

            // 处理订单
            orderService.processOrder(order);

            LOGGER.info("Order processed successfully: {}", order.getId());
            // 方法正常返回，自动 ack

        } catch (Exception e) {
            LOGGER.error("Failed to process order: {}", msg.getMsg(), e);
            // 抛出异常，自动 nack，消息会重新投递
            throw e;
        }
    }

    /**
     * 高并发消息处理
     */
    @ReceiveMsg(topic = "notification", consumerThreads = 10)
    public void handleNotification(RedisMsg msg) {
        String notification = msg.getMsg();
        // 10 个线程并发处理通知消息
        sendNotification(notification);
    }
}
```

## 监控和调试

可以通过日志查看消费者线程的状态：

```
Consumer thread started: ReceiveMsg-Consumer-1, topic: order-topic, method: OrderService.handleOrder
Received message: topic=order-topic, uuid=rmq123456, thread=ReceiveMsg-Consumer-1
Message acked: topic=order-topic, uuid=rmq123456
```

如果需要获取线程池状态，可以注入 `ReceiveMsgProcessor` 并调用：

```java
@Autowired
private ReceiveMsgProcessor receiveMsgProcessor;

public void checkStatus() {
    String status = receiveMsgProcessor.getThreadPoolStatus();
    System.out.println(status);
    // 输出: ReceiveMsgThreadPool[active=5, pool=10, queue=0, completed=1234]
}
```

## 与 @SendMsg 的配合使用

`@ReceiveMsg` 和 `@SendMsg` 可以完美配合使用，实现完整的消息生产-消费流程：

**生产者**：
```java
@SendMsg(topic = "order-topic", msgScript = "'order-' + args[0]")
public String createOrder(String orderId) {
    return "success-" + orderId;
}
```

**消费者**：
```java
@ReceiveMsg(topic = "order-topic")
public void handleOrder(RedisMsg msg) {
    String orderInfo = msg.getMsg();
    // 处理订单
}
```

## 常见问题

### Q1: 消费者线程数量有限制吗？

A: 理论上没有限制（受限于系统资源）。线程池使用 `SynchronousQueue` 和 `Integer.MAX_VALUE` 作为最大线程数，每个消费者都会立即获得独立线程。

### Q2: 如果消息处理失败会怎样？

A: 如果处理方法抛出异常，会自动调用 `nack()`，消息会在 `ackTimeout` 超时后重新投递。

### Q3: 消费者线程什么时候会被回收？

A: 当消费者线程空闲60秒后会被自动回收。但由于消费者线程会永久阻塞在 `receive()` 上，实际上只有在应用关闭时才会停止。

### Q4: 可以动态添加或删除消费者吗？

A: 不可以。消费者在应用启动时自动扫描并启动，运行期间不支持动态添加或删除。

### Q5: 多线程消费时消息顺序如何保证？

A: 无法保证。如果需要保证消息顺序，请使用单线程消费（`consumerThreads = 1`）。

