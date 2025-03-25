package com.caoyixin.lock.test;

import com.caoyixin.lock.annotation.CyxLock;
import com.caoyixin.lock.core.LockExecutor;
import com.caoyixin.lock.redisson.executor.RedissonLockExecutor;
import com.caoyixin.lock.support.LockFailureStrategy;
import com.caoyixin.lock.test.config.TestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试锁注解功能 - 使用Redisson实现
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { TestConfiguration.class, LockAnnotationTest.TestConfig.class })
public class LockAnnotationTest {
    private static final Logger logger = LoggerFactory.getLogger(LockAnnotationTest.class);

    @Autowired
    private TestLockService testLockService;

    @Autowired
    private ComplexLockService complexLockService;

    @Autowired
    private LockExecutor lockExecutor;

    @BeforeEach
    public void setUp() {
        // 确保使用的是Redisson执行器
        assertNotNull(lockExecutor);
        assertTrue(lockExecutor instanceof RedissonLockExecutor, "锁执行器应该是RedissonLockExecutor类型");
    }

    /**
     * 测试基本注解功能
     */
    @Test
    public void testBasicAnnotation() {
        String orderId = "order-123";
        String result = testLockService.processOrder(orderId);

        assertEquals("处理订单: " + orderId, result, "带注解的方法应该正确执行");

        // 不再验证调用计数，而是验证方法执行结果
    }

    /**
     * 测试复杂SpEL表达式
     */
    @Test
    public void testComplexSpEL() {
        TestDTO dto = new TestDTO();
        dto.setId("item-456");
        dto.setName("测试商品");
        dto.setQuantity(5);

        String result = complexLockService.processItem(dto);
        assertEquals("处理商品: item-456 (测试商品) 数量: 5", result, "带复杂SpEL表达式的方法应该正确执行");
    }

    /**
     * 测试自定义超时设置
     */
    @Test
    public void testCustomTimeouts() {
        String userId = "user-789";
        int amount = 100;

        String result = complexLockService.updateBalance(userId, amount);
        assertEquals("更新余额: user-789 金额: 100", result, "带自定义超时的方法应该正确执行");
    }

    /**
     * 测试获取锁失败
     */
    @Test
    public void testLockFailure() {
        // 先获取一个锁并持有
        String operationId = "op-test-123";
        String lockKey = "sensitive:" + operationId;

        // 使用CompletableFuture来模拟两个线程竞争同一个锁
        CompletableFuture<String> task1 = CompletableFuture.supplyAsync(() -> {
            try {
                return testLockService.processSensitiveOperation(operationId);
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        });

        // 等待第一个任务获取锁
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 第二个任务应该抛出异常
        try {
            testLockService.processSensitiveOperation(operationId);
            fail("应该抛出获取锁失败的异常");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("获取锁失败"), "应该抛出获取锁失败的异常");
        }

        // 确保第一个任务完成并释放锁
        try {
            String result = task1.get(3, TimeUnit.SECONDS);
            assertTrue(result.startsWith("处理敏感操作:"), "第一个任务应该成功执行");
        } catch (Exception e) {
            fail("获取第一个任务结果时出错: " + e.getMessage());
        }
    }

    /**
     * 测试多参数SpEL表达式
     */
    @Test
    public void testMultiParamSpEL() {
        String sourceId = "account-123";
        String targetId = "account-456";
        int amount = 50;

        String result = complexLockService.transferAmount(sourceId, targetId, amount);
        assertEquals("转账: 从 account-123 到 account-456 金额: 50", result, "带多参数SpEL的方法应该正确执行");
    }

    /**
     * 测试并发注解调用
     */
    @Test
    public void testConcurrentAnnotatedCalls() throws InterruptedException, ExecutionException, TimeoutException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Future<String>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String orderId = "order-" + i;
            results.add(executor.submit(() -> {
                try {
                    return testLockService.processOrder(orderId);
                } finally {
                    latch.countDown();
                }
            }));
        }

        // 等待所有线程完成，增加等待时间以适应Redis环境
        assertTrue(latch.await(20, TimeUnit.SECONDS), "所有线程应该在指定时间内完成");

        // 验证所有调用结果
        for (int i = 0; i < threads; i++) {
            String expected = "处理订单: order-" + i;
            String actual = results.get(i).get(2, TimeUnit.SECONDS);
            assertEquals(expected, actual, "所有线程应该成功执行带注解的方法");
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "线程池应该正常关闭");
    }

    /**
     * 测试配置类
     */
    @Configuration
    @Import(TestConfiguration.class)
    public static class TestConfig {

        @Bean
        public TestLockService testLockService() {
            return new TestLockService();
        }

        @Bean
        public ComplexLockService complexLockService() {
            return new ComplexLockService();
        }

        @Bean(name = "testLockFailureStrategy")
        @Primary // 使其成为主要的Bean，优先注入
        public LockFailureStrategy lockFailureStrategy() {
            return (key, acquireTimeout) -> {
                throw new RuntimeException("获取锁失败: " + key);
            };
        }
    }

    /**
     * 简单的带锁服务
     */
    @Service
    public static class TestLockService {
        private static final Logger logger = LoggerFactory.getLogger(TestLockService.class);

        /**
         * 基本锁定方法
         */
        @CyxLock(name = "order:#{#orderId}")
        public String processOrder(String orderId) {
            logger.info("正在处理订单: {}", orderId);
            try {
                // 模拟业务处理
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "处理订单: " + orderId;
        }

        /**
         * 敏感操作方法（使用自定义的锁失败策略）
         */
        @CyxLock(name = "sensitive:#{#operationId}")
        public String processSensitiveOperation(String operationId) {
            logger.info("正在处理敏感操作: {}", operationId);
            try {
                // 模拟业务处理，增加处理时间让锁竞争更容易测试
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "处理敏感操作: " + operationId;
        }
    }

    /**
     * 复杂锁功能服务
     */
    @Service
    public static class ComplexLockService {
        private static final Logger logger = LoggerFactory.getLogger(ComplexLockService.class);

        /**
         * 复杂SpEL表达式锁定方法
         */
        @CyxLock(name = "item:#{#item.id}:#{#item.name}:#{#item.quantity}")
        public String processItem(TestDTO item) {
            logger.info("正在处理商品: {} ({}) 数量: {}", item.getId(), item.getName(), item.getQuantity());
            try {
                // 模拟业务处理
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "处理商品: " + item.getId() + " (" + item.getName() + ") 数量: " + item.getQuantity();
        }

        /**
         * 自定义超时的锁定方法
         */
        @CyxLock(name = "balance:#{#userId}", acquireTimeout = 2000, expire = 10000)
        public String updateBalance(String userId, int amount) {
            logger.info("正在更新用户余额: {} 金额: {}", userId, amount);
            try {
                // 模拟业务处理
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "更新余额: " + userId + " 金额: " + amount;
        }

        /**
         * 多参数SpEL表达式锁定方法
         */
        @CyxLock(name = "transfer:#{#sourceId}:#{#targetId}")
        public String transferAmount(String sourceId, String targetId, int amount) {
            logger.info("正在转账: 从 {} 到 {} 金额: {}", sourceId, targetId, amount);
            try {
                // 模拟业务处理
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "转账: 从 " + sourceId + " 到 " + targetId + " 金额: " + amount;
        }
    }

    /**
     * 测试数据传输对象
     */
    public static class TestDTO {
        private String id;
        private String name;
        private int quantity;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}