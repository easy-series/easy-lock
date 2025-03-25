# CYX-Lock 分布式锁框架

CYX-Lock是一个基于Java的轻量级分布式锁框架，支持多种锁实现，适合在微服务或分布式系统中使用。

## 特性

- **多种锁实现**：目前支持基于Redisson的分布式锁实现
- **注解驱动**：支持@CyxLock注解简化使用
- **SpEL表达式**：支持使用SpEL表达式动态生成锁的key
- **自动超时释放**：防止死锁
- **可重入设计**：支持锁的重入
- **性能监控**：集成Micrometer，提供锁操作的度量指标
- **Spring Boot集成**：提供starter简化配置

## 快速开始

### 添加依赖

```xml
<dependency>
    <groupId>com.caoyixin</groupId>
    <artifactId>cyx-lock-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 配置Redisson连接

```properties
# application.properties
spring.redis.host=127.0.0.1
spring.redis.port=6379
```

### 使用注解

```java
@Service
public class UserService {
    
    @CyxLock(keys = {"#userId"})
    public User getUser(Long userId) {
        // 业务逻辑
    }
}
```

### 编程式使用

```java
@Service
public class OrderService {
    
    @Autowired
    private LockTemplate lockTemplate;
    
    public void createOrder(Order order) {
        lockTemplate.executeWithLock("order:" + order.getId(), 30000, 3000, () -> {
            // 业务逻辑
        });
    }
}
```

## 配置项

| 配置项                   | 说明                 | 默认值   |
| ------------------------ | -------------------- | -------- |
| cyx-lock.lock-key-prefix | 锁key前缀            | cyx:lock |
| cyx-lock.acquire-timeout | 获取锁超时时间(毫秒) | 3000     |
| cyx-lock.expire          | 锁过期时间(毫秒)     | 30000    |
| cyx-lock.metrics-enabled | 是否启用监控指标     | true     |

## 监控指标

CYX-Lock集成了Micrometer，提供以下监控指标：

- **cyx_lock_acquire_total**: 锁获取次数计数器
- **cyx_lock_acquire_failure_total**: 锁获取失败次数计数器
- **cyx_lock_acquire_time**: 锁获取时间直方图
- **cyx_lock_held_time**: 锁持有时间直方图
- **cyx_lock_active_count**: 当前活跃锁数量

## 模块结构

- **cyx-lock-core**: 核心接口和抽象实现
- **cyx-lock-redisson**: 基于Redisson的锁实现
- **cyx-lock-spring-boot-starter**: Spring Boot自动配置

## 如何扩展

### 添加新的锁实现

1. 创建新模块或包
2. 实现`LockExecutor`接口
3. 注册为Spring Bean

### 自定义锁键生成策略

实现`LockKeyBuilder`接口并注册为Bean

## 应用场景

- 防重复提交
- 秒杀场景
- 分布式任务调度
- 分布式限流
- 共享资源保护 