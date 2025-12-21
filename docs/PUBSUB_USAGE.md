# Redis Pub/Sub 发布订阅使用说明

## 功能介绍

Redis Pub/Sub（发布订阅）是 Redis 提供的一种消息通信模式，发送者（发布者）发送消息，订阅者接收消息。

**与 Redis 消息队列（send/receive）的区别：**
- **Pub/Sub**: 实时消息广播，无持久化，订阅者离线时消息会丢失，适合实时通知场景
- **消息队列**: 带 ACK 机制，消息持久化，支持消息重试，适合可靠消息传递场景

## API 方法

### 1. publish - 发布消息

发布消息到指定的 channel。

```java
Long publish(String channel, String message)
```

**参数：**
- `channel`: 频道名称
- `message`: 消息内容

**返回值：**
- 接收到消息的订阅者数量，发送失败返回 null

### 2. subscribe - 订阅频道

订阅指定的 channel，阻塞式接收一条消息后返回。

```java
String subscribe(String channel)
```

**参数：**
- `channel`: 要订阅的频道

**返回值：**
- 接收到的消息内容，如果发生异常返回 null

**注意：** 此方法会阻塞当前线程，直到接收到一条消息或发生异常。

## 使用示例

### 1. 基本的发布订阅

```java
@Autowired
private RedisHelper redisHelper;

// 在独立线程中订阅消息
new Thread(() -> {
    String message = redisHelper.subscribe("news-channel");
    System.out.println("收到消息: " + message);
}).start();

// 发布消息
Long subscribers = redisHelper.publish("news-channel", "今日新闻内容");
System.out.println("消息已发送给 " + subscribers + " 个订阅者");
```

### 2. 循环订阅消息

```java
new Thread(() -> {
    while (true) {
        try {
            String message = redisHelper.subscribe("order-channel");
            if (message != null) {
                System.out.println("收到订单消息: " + message);
                // 处理订单消息
            }
        } catch (Exception e) {
            System.err.println("订阅出错: " + e.getMessage());
            // 等待一段时间后重试
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}).start();
```

### 3. 使用线程池管理订阅者

```java
@Service
public class MessageSubscriberService {

    @Autowired
    private RedisHelper redisHelper;

    private ExecutorService executorService = Executors.newCachedThreadPool();

    @PostConstruct
    public void init() {
        // 启动订阅者线程
        executorService.submit(() -> {
            while (true) {
                try {
                    String message = redisHelper.subscribe("notification-channel");
                    if (message != null) {
                        handleMessage(message);
                    }
                } catch (Exception e) {
                    System.err.println("订阅出错: " + e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void handleMessage(String message) {
        try {
            // 处理消息逻辑
            System.out.println("处理通知: " + message);
        } catch (Exception e) {
            System.err.println("处理消息失败: " + e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdown();
    }
}
```

## 最佳实践

1. **独立线程订阅**: subscribe 方法会阻塞线程，务必在独立线程中调用
2. **循环订阅**: 通常需要在循环中调用 subscribe，以持续接收消息
3. **异常处理**: 做好异常处理，避免订阅线程意外退出
4. **选择合适的模式**:
   - 需要可靠消息传递时使用消息队列（send/receive）
   - 需要实时广播通知时使用 Pub/Sub
5. **资源管理**: 合理管理订阅线程，应用关闭时及时清理资源

## 注意事项

1. Pub/Sub 消息不会持久化，订阅者离线时消息会丢失
2. subscribe 会阻塞当前线程，直到接收到一条消息或发生异常
3. 每次调用 subscribe 只接收一条消息，需要循环调用以持续接收
4. 发布消息时，如果没有订阅者，消息会被丢弃
5. subscribe 方法在接收到消息后会自动取消订阅

