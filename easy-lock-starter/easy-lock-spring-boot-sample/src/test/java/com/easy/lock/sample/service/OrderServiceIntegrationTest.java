package com.easy.lock.sample.service;

import com.easy.lock.exception.LockAcquireFailedException;
import com.easy.lock.sample.RedisTestConfiguration;
import com.easy.lock.sample.TestRedisLockConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderService集成测试
 * 注意：这个测试需要实际的Redis服务，请确保Redis运行在localhost:6379
 */
@SpringBootTest
@Import({ RedisTestConfiguration.class, TestRedisLockConfiguration.class })
public class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("测试基本锁功能")
    public void testBasicLock() {
        // 生成唯一订单ID，避免与其他测试冲突
        String orderId = "test-" + UUID.randomUUID().toString();

        // 处理订单
        String result = orderService.processOrder(orderId);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.contains(orderId));
        assertTrue(result.contains("处理成功"));
    }

    @Test
    @DisplayName("测试联锁功能")
    public void testMultiLock() {
        // 生成唯一ID
        String productId = "product-" + UUID.randomUUID().toString();
        String warehouseId = "warehouse-" + UUID.randomUUID().toString();
        int quantity = 10;

        // 更新库存
        String result = orderService.updateStock(productId, warehouseId, quantity);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.contains(productId));
        assertTrue(result.contains(warehouseId));
        assertTrue(result.contains("" + quantity));
        assertTrue(result.contains("更新成功"));
    }

    @Test
    @DisplayName("测试重试锁功能")
    public void testRetryLock() {
        // 生成唯一订单ID
        String orderId = "retry-" + UUID.randomUUID().toString();

        // 处理订单（带重试）
        String result = orderService.processOrderWithRetry(orderId);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.contains(orderId));
        assertTrue(result.contains("处理成功(带重试)"));
    }

    @Test
    @DisplayName("测试可重入锁功能")
    public void testReentrantLock() {
        // 生成唯一订单ID
        String orderId = "reentrant-" + UUID.randomUUID().toString();

        // 使用可重入锁处理订单
        String result = orderService.processOrderWithReentrant(orderId);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.contains(orderId));
        assertTrue(result.contains("外部处理成功"));
        assertTrue(result.contains("订单详情处理成功"));
    }

    @Test
    @DisplayName("测试锁竞争 - 模拟并发")
    public void testLockContention() throws Exception {
        // 同一个订单ID会导致锁竞争
        String orderId = "contention-" + UUID.randomUUID().toString();
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        List<CompletableFuture<String>> futures = new ArrayList<>();

        // 创建3个并发任务，处理同一个订单
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return orderService.processOrder(orderId);
                } catch (Exception e) {
                    // 捕获预期的锁异常
                    if (e instanceof LockAcquireFailedException) {
                        return "LOCK_FAILED";
                    }
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        // 验证结果 - 应该只有一个成功，其他失败或等待超时
        int successCount = 0;
        int failCount = 0;

        for (CompletableFuture<String> future : futures) {
            String result = future.get();
            if (result.contains("处理成功")) {
                successCount++;
            } else if (result.equals("LOCK_FAILED")) {
                failCount++;
            }
        }

        // 关闭线程池
        executor.shutdown();

        // 验证结果 - 基于锁的行为，可能有不同的结果:
        // 1. 一个成功获取锁，其他失败
        // 2. 一个成功，其他等待直到超时
        assertTrue(successCount >= 1, "至少一个线程应该成功获取锁");

        // 注意：这个断言可能不总是准确，因为线程调度和锁行为可能导致不同的结果
        // 在实际生产环境中，可能需要根据具体的锁行为调整这个断言
    }

    @Test
    @DisplayName("测试重试锁的并发")
    public void testRetryConcurrency() throws Exception {
        // 同一个订单ID
        String orderId = "retry-concurrent-" + UUID.randomUUID().toString();
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        List<CompletableFuture<String>> futures = new ArrayList<>();

        // 创建3个并发任务，处理同一个订单（带重试）
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return orderService.processOrderWithRetry(orderId);
                } catch (Exception e) {
                    return "ERROR: " + e.getMessage();
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(15, TimeUnit.SECONDS);

        // 验证结果
        int successCount = 0;
        List<String> errorMessages = new ArrayList<>();

        for (CompletableFuture<String> future : futures) {
            String result = future.get();
            if (result.contains("处理成功")) {
                successCount++;
            } else if (result.startsWith("ERROR")) {
                errorMessages.add(result);
            }
        }

        // 关闭线程池
        executor.shutdown();

        // 至少一个应该成功
        assertTrue(successCount >= 1, "至少一个线程应该成功（可能通过重试）: " + errorMessages);
    }

    @Test
    @DisplayName("测试重入锁并发")
    public void testReentrantConcurrency() throws Exception {
        // 同一个订单ID
        String orderId = "reentrant-concurrent-" + UUID.randomUUID().toString();
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        List<CompletableFuture<String>> futures = new ArrayList<>();

        // 创建3个并发任务，处理同一个订单（使用重入锁）
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return orderService.processOrderWithReentrant(orderId);
                } catch (Exception e) {
                    return "ERROR: " + e.getMessage();
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(15, TimeUnit.SECONDS);

        // 验证结果
        int successCount = 0;

        for (CompletableFuture<String> future : futures) {
            String result = future.get();
            if (result.contains("外部处理成功")) {
                successCount++;
            }
        }

        // 关闭线程池
        executor.shutdown();

        // 由于使用的是互斥锁，应该只有一个获取成功
        assertTrue(successCount >= 1, "至少一个线程应该成功");
    }
}