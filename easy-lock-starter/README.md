# Easy-Lock 分布式锁框架

Easy-Lock 是一个简单易用的 Spring Boot 分布式锁框架，支持 Redis 和数据库两种锁实现方式，可以轻松地在应用中实现分布式锁功能。

## 功能特性

- **多种锁实现方式**：支持 Redis（基于 Redisson）和 JDBC 数据库两种锁实现。
- **注解驱动**：使用 `@Lock` 注解即可轻松实现分布式锁功能，无需编写复杂代码。
- **灵活的锁类型**：支持普通锁、尝试锁和联锁（多把锁同时获取）。
- **SpEL 表达式支持**：锁的 key 支持 SpEL 表达式，可以动态生成锁的 key。
- **多种失败策略**：支持抛出异常、返回空值、继续执行三种失败处理策略。
- **可重入锁支持**：同一线程可以多次获取同一把锁，避免死锁。
- **锁重试机制**：支持获取锁失败时自动重试，可配置重试次数、重试间隔和重试策略。
- **锁监控**：集成 Micrometer，提供锁获取、释放等指标监控。
- **自动配置**：自动适配 Spring Boot 环境，零配置即可使用。
- **优雅实现**：基于 AOP 实现，对代码无侵入。

## 快速开始

### 1. 添加依赖

在 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.easy</groupId>
    <artifactId>easy-lock-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置属性

在 `application.yml` 中添加配置：

```yaml
easy:
  lock:
    # 是否启用分布式锁
    enabled: true
    # 锁的key前缀
    prefix: lock
    # 等待获取锁的时间（毫秒）
    wait-time: 3000
    # 持有锁的时间（毫秒）
    lease-time: 30000
    # 时间单位（默认毫秒）
    time-unit: MILLISECONDS
    # 锁类型，可选值：REDIS、JDBC
    lock-type: REDIS
    
    # Redis配置（使用Redis锁时需要）
    redis:
      address: redis://localhost:6379
      password: 
      database: 0
      pool-size: 64
      min-idle: 24
      connect-timeout: 10000
      
    # JDBC配置（使用JDBC锁时需要）
    jdbc:
      table-name: distributed_lock
      create-table: true
      expire-time: 60000
```

### 3. 使用注解

在需要加锁的方法上添加 `@Lock` 注解：

```java
import com.easy.lock.annotation.Lock;
import java.util.concurrent.TimeUnit;

@Service
public class OrderService {

    // 简单使用方式
    @Lock(key = "'order:' + #orderId")
    public void processOrder(String orderId) {
        // 业务处理逻辑
    }
    
    // 带超时的尝试锁
    @Lock(
        key = "'stock:' + #productId", 
        waitTime = 5000, 
        leaseTime = 10000, 
        timeUnit = TimeUnit.MILLISECONDS,
        failStrategy = Lock.FailStrategy.EXCEPTION
    )
    public void reduceStock(String productId, int quantity) {
        // 业务处理逻辑
    }
    
    // 联锁（同时锁定多个资源）
    @Lock(
        type = Lock.LockType.MULTI_LOCK,
        keys = {"'product:' + #productId", "'warehouse:' + #warehouseId"},
        waitTime = 3000,
        leaseTime = 5000
    )
    public void transferStock(String productId, String warehouseId, int quantity) {
        // 业务处理逻辑
    }
    
    // 使用读锁
    @Lock(
        key = "'order:' + #orderId", 
        mode = Lock.LockMode.READ, 
        failStrategy = Lock.FailStrategy.RETURN_NULL
    )
    public OrderInfo queryOrder(String orderId) {
        // 业务处理逻辑
        return orderInfo;
    }
    
    // 带重试机制的锁
    @Lock(
        key = "'payment:' + #paymentId",
        retryEnabled = true,
        maxRetries = 3,
        retryInterval = 1000,
        retryStrategy = Lock.RetryStrategy.EXPONENTIAL
    )
    public void processPayment(String paymentId) {
        // 业务处理逻辑
    }
    
    // 可重入锁示例 - 外部方法
    @Lock(key = "'order:reentrant:' + #orderId")
    public void processOrderWithReentrant(String orderId) {
        // 业务逻辑
        
        // 调用内部方法，内部方法也会获取同一把锁（重入）
        processOrderDetail(orderId);
    }
    
    // 可重入锁示例 - 内部方法
    @Lock(key = "'order:reentrant:' + #orderId")
    public void processOrderDetail(String orderId) {
        // 业务逻辑，可以重入同一把锁
    }
}
```

## 注解参数说明

`@Lock` 注解支持以下参数：

| 参数          | 类型          | 默认值       | 说明                                                                                             |
| ------------- | ------------- | ------------ | ------------------------------------------------------------------------------------------------ |
| prefix        | String        | ""           | 锁的key前缀，默认使用配置文件中的prefix值                                                        |
| key           | String        | ""           | 锁的key，支持SpEL表达式，如果不指定，将使用类名:方法名作为key                                    |
| keys          | String[]      | {}           | 多个锁的key，支持SpEL表达式，用于联锁                                                            |
| type          | LockType      | TRY_LOCK     | 锁类型，支持TRY_LOCK（尝试获取锁）、LOCK（永久锁）、MULTI_LOCK（联锁）                           |
| mode          | LockMode      | WRITE        | 锁模式，支持WRITE（写锁）、READ（读锁）                                                          |
| waitTime      | long          | -1           | 等待获取锁的时间，默认使用配置文件中的waitTime值                                                 |
| leaseTime     | long          | -1           | 持有锁的时间，默认使用配置文件中的leaseTime值                                                    |
| timeUnit      | TimeUnit      | MILLISECONDS | 时间单位，默认使用配置文件中的timeUnit值                                                         |
| failStrategy  | FailStrategy  | EXCEPTION    | 获取锁失败时的处理策略，支持EXCEPTION（抛出异常）、RETURN_NULL（返回空值）、CONTINUE（继续执行） |
| retryEnabled  | boolean       | false        | 是否启用重试机制，仅在failStrategy为EXCEPTION时有效                                              |
| maxRetries    | int           | 3            | 最大重试次数，仅在retryEnabled为true时有效                                                       |
| retryInterval | long          | 1000         | 重试间隔（毫秒），仅在retryEnabled为true时有效                                                   |
| retryStrategy | RetryStrategy | FIXED        | 重试策略，支持FIXED（固定间隔）、EXPONENTIAL（指数退避），仅在retryEnabled为true时有效           |

## 进阶特性

### 1. 锁类型

框架支持三种锁类型：

- **TRY_LOCK**：尝试在指定时间内获取锁，如果获取不到则根据失败策略处理。
- **LOCK**：一直尝试获取锁，直到获取成功或被中断。
- **MULTI_LOCK**：同时获取多把锁（联锁），保证所有资源同时被锁定，避免死锁。

### 2. 锁模式

框架支持两种锁模式：

- **WRITE**：写锁（排他锁），同一时间只允许一个线程持有锁。
- **READ**：读锁（共享锁），多个线程可以同时持有读锁，但与写锁互斥。

### 3. 失败策略

当获取锁失败时，可以采用三种策略：

- **EXCEPTION**：抛出 LockAcquireFailedException 异常。
- **RETURN_NULL**：直接返回 null。
- **CONTINUE**：继续执行方法（不加锁）。

### 4. 重试机制

当获取锁失败时，可以自动重试：

- **FIXED**：固定时间间隔重试，每次重试间隔相同。
- **EXPONENTIAL**：指数退避重试，重试间隔随着重试次数增加而增长，公式为 `interval * (factor ^ retryCount)`。

### 5. 可重入锁

同一线程可以多次获取同一把锁，框架会维护一个计数器，当计数器为0时才会真正释放锁。

### 6. 监控指标

框架集成了 Micrometer，提供以下监控指标：

- **lock.acquire.total**：锁获取总次数。
- **lock.acquire.failure**：锁获取失败次数。
- **lock.wait.time**：等待获取锁的时间。
- **lock.hold.time**：持有锁的时间。

## 实现原理

### Redis 锁实现

基于 Redisson 的 RLock 实现，提供了可靠的分布式锁机制，支持锁超时和自动续期。

### JDBC 锁实现

基于数据库的锁表实现，通过唯一索引保证锁的互斥性，支持锁超时自动清理。

### 可重入锁实现

通过 ThreadLocal 存储锁的上下文，同一线程多次获取同一把锁时增加计数器，避免死锁。

### 重试机制实现

使用 LockRetryTemplate 封装重试逻辑，支持不同的重试策略和重试间隔。

## 常见问题

### Q: 如何切换锁实现？

A: 在配置文件中修改 `easy.lock.lock-type` 属性，可选值为 `REDIS` 或 `JDBC`。

### Q: 锁的重入性如何保证？

A: Redis 锁基于 Redisson 实现，天然支持重入；JDBC 锁通过维护线程与锁的映射关系，确保同一线程能够重入同一把锁。

### Q: 如何处理锁超时问题？

A: 可以设置适当的 `lease-time` 值，确保业务处理时间不会超过锁的持有时间。对于长时间运行的任务，建议使用 `LOCK` 类型，由框架自动续期。

### Q: 获取锁失败时如何优雅处理？

A: 使用 `failStrategy` 属性设置失败处理策略，例如返回错误信息；或者启用重试机制 `retryEnabled = true`，自动重试获取锁。

### Q: 如何保证锁的可靠性？

A: Redis 锁采用 Redisson 实现，具有自动续期、跨进程可见等特性；JDBC 锁通过数据库的事务和唯一索引保证可靠性。

## 版本兼容性

- Spring Boot: 2.6.0+
- Java: 1.8+
- Redisson: 3.23.0+

## 贡献指南

欢迎提交 Pull Request 或 Issue，一起完善这个框架。

## 许可证

本项目采用 [MIT 许可证](LICENSE)。 