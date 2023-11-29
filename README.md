# redis-helper
useful redis features

```xml
<dependency>
	<groupId>com.pugwoo</groupId>
	<artifactId>redis-helper</artifactId>
	<version>1.3.2</version>
</dependency>
```

Fully tests with Spring Boot 2.2.x ~ 2.7.x, jedis 3.1.0 ~ 3.10.0

特别说明：默认不支持Spring Boot 3.x，因为其默认使用jedis 4.x/5.x的版本，这两个版本的redis.clients.jedis.ScanResult的包移动路径了。考虑到Spring 3.x的用户还比较少，所以暂不升级到jedis 4.x/5.x。如果确实升级到了Spring Boot 3.x，请指定jedis版本为3.10.0。