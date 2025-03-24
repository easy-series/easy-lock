# Easy-Lock 示例应用

这个示例项目演示了如何在 Spring Boot 应用中使用 Easy-Lock 分布式锁框架。

## 环境准备

1. MySQL 数据库（对于 JDBC 锁）
2. Redis 服务（对于 Redis 锁）

## 配置说明

配置文件位于 `src/main/resources/application.yml`，包含以下核心配置：

```yaml
easy:
  lock:
    enabled: true
    prefix: "easy-lock:"
    wait-time: 3000
    lease-time: 30000
    time-unit: MILLISECONDS
    lock-type: REDIS  # 可选：REDIS、JDBC
    # 重试相关配置
    retry:
      enabled: true
      max-retries: 3
      retry-interval: 1000
      retry-strategy: EXPONENTIAL
```

## 启动应用

1. 确保 MySQL 和 Redis 服务已启动
2. 执行以下命令启动应用：

```bash
mvn spring-boot:run
```

## API 接口说明

### 基本锁功能

1. 处理订单（基本锁）
   ```
   GET /api/order/process/{orderId}
   ```

2. 更新库存（联锁）
   ```
   GET /api/order/stock/{productId}/{warehouseId}/{quantity}
   ```

3. 查询订单（读锁）
   ```
   GET /api/order/query/{orderId}
   ```

4. 并发处理订单（测试锁竞争）
   ```
   GET /api/order/concurrent/{orderId}
   ```

### 重试功能

1. 带重试的订单处理
   ```
   GET /api/order/retry/{orderId}
   ```

2. 并发带重试的订单处理
   ```
   GET /api/order/concurrent-retry/{orderId}
   ```

### 可重入锁功能

1. 可重入锁订单处理
   ```
   GET /api/order/reentrant/{orderId}
   ```

2. 并发可重入锁订单处理
   ```
   GET /api/order/concurrent-reentrant/{orderId}
   ```

## 测试场景

### 场景一：基本锁功能

1. 启动应用
2. 打开两个浏览器窗口
3. 在第一个窗口请求：`http://localhost:8080/api/order/process/123`
4. 立即在第二个窗口请求：`http://localhost:8080/api/order/process/123`
5. 观察日志，第二个请求应该会等待或失败

### 场景二：重试功能

1. 在一个窗口请求：`http://localhost:8080/api/order/concurrent-retry/456`
2. 观察日志，可以看到当锁获取失败时，会进行重试

### 场景三：可重入锁功能

1. 请求：`http://localhost:8080/api/order/reentrant/789`
2. 观察日志，内部方法能够重入获取同一把锁

### 场景四：并发可重入锁

1. 请求：`http://localhost:8080/api/order/concurrent-reentrant/789`
2. 观察日志，同一个线程的请求能够重入，不同线程的请求会等待锁释放

## 性能测试

可以使用 JMeter 或其他压测工具进行性能测试：

1. 配置 HTTP 请求：`http://localhost:8080/api/order/process/test-{random}`
2. 设置线程数：50
3. 设置循环次数：10
4. 运行测试，观察锁的性能表现

## 日志分析

应用日志中包含详细的锁获取和释放信息，可以通过分析日志了解锁的运行情况：

1. 锁获取时间
2. 锁持有时间
3. 锁释放情况
4. 重试次数和间隔
5. 可重入计数

## 故障排除

1. 如果遇到锁获取超时，检查 `wait-time` 参数是否设置合理
2. 如果遇到锁提前释放，检查 `lease-time` 参数是否足够处理业务逻辑
3. Redis 连接问题会导致锁获取失败，检查 Redis 配置是否正确
4. 数据库连接问题会影响 JDBC 锁，检查数据库配置是否正确 