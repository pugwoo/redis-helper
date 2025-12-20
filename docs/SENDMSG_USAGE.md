# @SendMsg 注解使用说明

## 功能介绍

`@SendMsg` 注解用于在方法成功返回后自动发送消息到 Redis 消息队列。功能等价于手工调用 `redisHelper.send()` 方法。

## 注解参数

- **topic** (必须): 消息的 topic，即 Redis 的 key
- **defaultAckTimeoutSec** (可选): 默认 ack 超时时间（秒），默认值为 3600 秒（1小时）
- **msgScript** (必须): MVEL 表达式，用于构造消息内容，可以从以下变量获取数据：
  - `args`: 方法的参数数组（Object[]）
  - `ret`: 方法的返回值

## 使用示例

### 1. 基本用法

```java
@SendMsg(topic = "order-topic", msgScript = "'order-' + args[0]")
public String createOrder(String orderId) {
    // 业务逻辑
    return "success-" + orderId;
}
```

当方法成功返回后，会自动发送消息 `"order-{orderId}"` 到 `order-topic`。

### 2. 使用返回值

```java
@SendMsg(
    topic = "order-result-topic", 
    msgScript = "args[0] + ':' + ret",
    defaultAckTimeoutSec = 60
)
public String processOrder(String orderId) {
    // 业务逻辑
    return "processed";
}
```

消息内容为 `"{orderId}:processed"`，ack 超时时间为 60 秒。

### 3. 使用复杂对象

```java
@SendMsg(
    topic = "order-complex-topic",
    msgScript = "args[0].orderId + ',' + args[0].amount + ',' + ret.status"
)
public OrderResult createComplexOrder(OrderRequest request) {
    OrderResult result = new OrderResult();
    result.setOrderId(request.getOrderId());
    result.setStatus("SUCCESS");
    return result;
}
```

### 4. 多个消息发送

使用 `@SendMsgs` 注解可以在一个方法上发送多个消息到不同的 topic：

```java
@SendMsgs({
    @SendMsg(topic = "topic1", msgScript = "'msg1-' + args[0]"),
    @SendMsg(topic = "topic2", msgScript = "'msg2-' + ret", defaultAckTimeoutSec = 120)
})
public String multiTopicOrder(String orderId) {
    // 业务逻辑
    return "result-" + orderId;
}
```

## 配置

在 Spring 配置类中注册 `SendMsgAspect`：

```java
@Configuration
public class RedisHelperConfiguration {
    
    @Bean
    public SendMsgAspect sendMsgAspect() {
        return new SendMsgAspect();
    }
    
    // 其他配置...
}
```

## 注意事项

1. **只有方法成功返回时才会发送消息**：如果方法抛出异常，消息不会被发送
2. **msgScript 执行失败不会影响方法执行**：如果 msgScript 执行出错，会打印日志，但不会抛出异常阻止方法返回
3. **消息发送失败不会影响方法执行**：如果消息发送失败，会打印错误日志，但不会影响方法的正常返回
4. **需要配置 RedisHelper**：确保 Spring 容器中有 `RedisHelper` bean

## MVEL 表达式示例

```java
// 简单字符串拼接
msgScript = "'prefix-' + args[0]"

// 访问对象属性
msgScript = "args[0].userId + ',' + args[0].orderId"

// 使用返回值
msgScript = "ret.status + ':' + ret.message"

// 复杂表达式
msgScript = "args[0].orderId + ',' + (ret.success ? 'SUCCESS' : 'FAILED')"

// JSON 格式（需要手动构造）
msgScript = "'{\"orderId\":\"' + args[0] + '\",\"status\":\"' + ret + '\"}'"
```

## 消息接收

消息发送后，可以使用 `RedisHelper.receive()` 方法接收：

```java
RedisMsg msg = redisHelper.receive("order-topic", 5, null);
if (msg != null) {
    System.out.println("Received: " + msg.getMsg());
    redisHelper.ack("order-topic", msg.getUuid());
}
```

