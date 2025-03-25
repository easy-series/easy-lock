# cyx-lock 多模块框架设计方案

## 一、整体架构

cyx-lock 采用Maven多模块架构，将不同功能组件解耦到独立模块，提高代码的复用性和可维护性。

```
cyx-lock/                      # 父项目
├── cyx-lock-core/             # 核心模块，包含基础接口和抽象实现
├── cyx-lock-redisson/         # Redisson实现模块
└── cyx-lock-spring-boot-starter/ # Spring Boot自动配置模块
```

## 二、模块职责分工

### 1. cyx-lock-core

此模块包含框架的核心抽象和接口定义，不依赖任何特定的分布式锁实现。

- 定义锁相关的接口和抽象类
- 提供锁模板、锁信息等核心对象
- 包含SpEL表达式处理、锁键生成等通用功能
- 无任何特定技术的实现依赖

### 2. cyx-lock-redisson

基于Redisson客户端的分布式锁具体实现。

- 依赖cyx-lock-core模块
- 实现基于Redisson的锁获取和释放逻辑
- 提供Redisson特有配置项

### 3. cyx-lock-spring-boot-starter

Spring Boot集成模块，提供自动配置功能。

- 依赖cyx-lock-core和cyx-lock-redisson模块
- 提供Spring Boot自动配置类
- 装配AOP切面支持注解驱动
- 管理配置属性映射

## 三、具体模块设计

### 1. cyx-lock-core 模块

#### 1.1 目录结构

```
cyx-lock-core/
├── src/main/java/com/caoyixin/lock/
│   ├── annotation/        # 注解定义
│   ├── core/              # 核心接口和类
│   ├── exception/         # 异常类
│   ├── support/           # 支持类
│   └── util/              # 工具类
```

#### 1.2 核心接口和类

```java
// 锁信息
public class LockInfo {
    private String key;             // 锁的key
    private String value;           // 锁的值，用于标识锁的持有者
    private Long expireTime;        // 锁的过期时间戳
    private Integer reentrantCount; // 重入计数
}

// 锁执行器接口
public interface LockExecutor {
    // 获取锁
    LockInfo acquire(String key, long expire, long acquireTimeout);
    
    // 释放锁
    boolean release(LockInfo lockInfo);
    
    // 查询锁状态
    boolean isLocked(String key);
}

// 锁模板
public class LockTemplate {
    private LockExecutor lockExecutor;
    
    // 基本锁操作
    public LockInfo lock(String key, long expire, long acquireTimeout);
    public boolean releaseLock(LockInfo lockInfo);
    
    // 函数式方法
    public <T> T executeWithLock(String key, long expire, long acquireTimeout, Supplier<T> supplier);
    public void executeWithLock(String key, long expire, long acquireTimeout, Runnable runnable);
}

// 锁注解
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EasyLock {
    String[] keys() default {};
    long expire() default 30000;
    long acquireTimeout() default 3000;
    boolean autoRelease() default true;
}

// 锁Key构建器接口
public interface LockKeyBuilder {
    String buildKey(ProceedingJoinPoint joinPoint, String[] keys);
}

// 锁失败策略接口
public interface LockFailureStrategy {
    Object onLockFailure(String key, long acquireTimeout);
}
```

### 2. cyx-lock-redisson 模块

#### 2.1 目录结构

```
cyx-lock-redisson/
├── src/main/java/com/caoyixin/lock/redisson/
│   ├── executor/          # Redisson锁执行器实现
│   └── config/            # Redisson相关配置
```

#### 2.2 主要类

```java
// Redisson锁执行器实现
public class RedissonLockExecutor implements LockExecutor {
    private RedissonClient redissonClient;
    
    @Override
    public LockInfo acquire(String key, long expire, long acquireTimeout) {
        RLock lock = redissonClient.getLock(key);
        try {
            boolean success = lock.tryLock(acquireTimeout, expire, TimeUnit.MILLISECONDS);
            if (success) {
                LockInfo lockInfo = new LockInfo();
                lockInfo.setKey(key);
                lockInfo.setValue(key);
                lockInfo.setExpireTime(System.currentTimeMillis() + expire);
                return lockInfo;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
    
    @Override
    public boolean release(LockInfo lockInfo) {
        RLock lock = redissonClient.getLock(lockInfo.getKey());
        if (lock.isLocked() && lock.isHeldByCurrentThread()) {
            lock.unlock();
            return true;
        }
        return false;
    }
    
    @Override
    public boolean isLocked(String key) {
        RLock lock = redissonClient.getLock(key);
        return lock.isLocked();
    }
}
```

### 3. cyx-lock-spring-boot-starter 模块

#### 3.1 目录结构

```
cyx-lock-spring-boot-starter/
├── src/main/java/com/caoyixin/lock/starter/
│   ├── autoconfigure/     # 自动配置类
│   ├── aspect/            # AOP切面
│   └── properties/        # 配置属性类
└── src/main/resources/
    └── META-INF/
        └── spring.factories # 自动配置声明
```

#### 3.2 主要类

```java
// 锁配置属性
@ConfigurationProperties(prefix = "cyx-lock")
public class LockProperties {
    private String lockKeyPrefix = "easy:lock";
    private long acquireTimeout = 3000;
    private long expire = 30000;
    // getter和setter...
}

// 自动配置类
@Configuration
@ConditionalOnClass({RedissonClient.class, LockTemplate.class})
@EnableConfigurationProperties(LockProperties.class)
public class LockAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public LockExecutor lockExecutor(RedissonClient redissonClient) {
        RedissonLockExecutor executor = new RedissonLockExecutor();
        executor.setRedissonClient(redissonClient);
        return executor;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate(LockExecutor lockExecutor) {
        LockTemplate template = new LockTemplate();
        template.setLockExecutor(lockExecutor);
        return template;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public LockKeyBuilder lockKeyBuilder(LockProperties properties) {
        DefaultLockKeyBuilder keyBuilder = new DefaultLockKeyBuilder();
        keyBuilder.setLockKeyPrefix(properties.getLockKeyPrefix());
        return keyBuilder;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public LockFailureStrategy lockFailureStrategy() {
        return new DefaultLockFailureStrategy();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(LockTemplate lockTemplate, 
                               LockKeyBuilder keyBuilder,
                               LockFailureStrategy failureStrategy) {
        LockAspect aspect = new LockAspect();
        aspect.setLockTemplate(lockTemplate);
        aspect.setKeyBuilder(keyBuilder);
        aspect.setFailureStrategy(failureStrategy);
        return aspect;
    }
}

// AOP切面
@Aspect
public class LockAspect {
    private LockTemplate lockTemplate;
    private LockKeyBuilder keyBuilder;
    private LockFailureStrategy failureStrategy;
    
    @Around("@annotation(com.caoyixin.lock.annotation.EasyLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 提取注解信息
        // 构建锁key
        // 获取锁
        // 执行目标方法
        // 释放锁
    }
}
```

## 四、模块依赖关系

```
cyx-lock-spring-boot-starter
        ↓
    依赖
        ↓
cyx-lock-redisson
        ↓
    依赖
        ↓
cyx-lock-core
```

## 五、POM文件设计

### 1. 父模块 (cyx-lock)

```xml
<project>
    <groupId>com.caoyixin</groupId>
    <artifactId>cyx-lock</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <modules>
        <module>cyx-lock-core</module>
        <module>cyx-lock-redisson</module>
        <module>cyx-lock-spring-boot-starter</module>
    </modules>
    
    <properties>
        <java.version>1.8</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>2.6.3</spring-boot.version>
        <redisson.version>3.17.0</redisson.version>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <!-- 项目模块 -->
            <dependency>
                <groupId>com.caoyixin</groupId>
                <artifactId>cyx-lock-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.caoyixin</groupId>
                <artifactId>cyx-lock-redisson</artifactId>
                <version>${project.version}</version>
            </dependency>
            
            <!-- 第三方依赖 -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.redisson</groupId>
                <artifactId>redisson</artifactId>
                <version>${redisson.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 2. 核心模块 (cyx-lock-core)

```xml
<project>
    <parent>
        <groupId>com.caoyixin</groupId>
        <artifactId>cyx-lock</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    
    <artifactId>cyx-lock-core</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

### 3. Redisson模块 (cyx-lock-redisson)

```xml
<project>
    <parent>
        <groupId>com.caoyixin</groupId>
        <artifactId>cyx-lock</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    
    <artifactId>cyx-lock-redisson</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>com.caoyixin</groupId>
            <artifactId>cyx-lock-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 4. Spring Boot Starter模块 (cyx-lock-spring-boot-starter)

```xml
<project>
    <parent>
        <groupId>com.caoyixin</groupId>
        <artifactId>cyx-lock</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    
    <artifactId>cyx-lock-spring-boot-starter</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>com.caoyixin</groupId>
            <artifactId>cyx-lock-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.caoyixin</groupId>
            <artifactId>cyx-lock-redisson</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

## 六、使用示例

### 基本使用

```java
// 1. 引入依赖
// <dependency>
//     <groupId>com.caoyixin</groupId>
//     <artifactId>cyx-lock-spring-boot-starter</artifactId>
//     <version>0.0.1-SNAPSHOT</version>
// </dependency>

// 2. 配置Redisson
// spring.redis.host=127.0.0.1
// spring.redis.port=6379

// 3. 注解方式使用
@Service
public class UserService {
    
    @EasyLock(keys = {"#userId"})
    public User getUser(Long userId) {
        // 业务逻辑
    }
}

// 4. 编程式使用
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

## 七、扩展性设计

模块化设计使cyx-lock框架具有良好的扩展性：

1. **可添加新的锁实现**：只需创建新模块，实现`LockExecutor`接口
2. **可自定义锁键生成策略**：实现`LockKeyBuilder`接口
3. **可自定义锁获取失败策略**：实现`LockFailureStrategy`接口
4. **可定制SpringBoot自动配置**：扩展或替换`LockAutoConfiguration`

## 八、应用场景

1. **防重复提交**：表单提交、支付请求等
2. **秒杀场景**：控制商品库存并发修改
3. **分布式任务调度**：保证定时任务单实例执行
4. **分布式限流**：控制接口访问频率
5. **共享资源保护**：数据一致性保障根据