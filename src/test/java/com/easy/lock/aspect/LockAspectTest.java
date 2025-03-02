package com.easy.lock.aspect;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.easy.lock.AbstractIntegrationTest;
import com.easy.lock.annotation.Lock;
import com.easy.lock.exception.LockAcquireFailedException;
import com.easy.lock.monitor.LockMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * 锁切面测试
 */
class LockAspectTest extends AbstractIntegrationTest {

    @Autowired
    private TestLockService testLockService;

    @Autowired
    private LockMetrics lockMetrics;

    @Autowired
    private RedissonClient redissonClient;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(5);
        lockMetrics.clearStats();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void testTryLock() throws InterruptedException {
        String key = "test1";
        testLockService.tryLockMethod(key);

        // 验证锁指标
        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        assertTrue(stats.containsKey("test-lock:" + key), "应该包含锁的统计信息");
        assertEquals(1, stats.get("test-lock:" + key).getAcquireCount(), "获取次数应该为1");
    }

    @Test
    void testTryLockConcurrent() throws InterruptedException {
        String key = "test2";
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        int threadCount = 3;
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 多线程并发获取同一个锁
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    testLockService.tryLockMethod(key);
                    successCount.incrementAndGet();
                } catch (LockAcquireFailedException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);

        // 验证结果
        assertEquals(1, successCount.get(), "只应该有一个线程成功获取锁");
        assertEquals(2, failCount.get(), "应该有两个线程获取锁失败");

        // 验证锁指标
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
            assertTrue(stats.containsKey("test-lock:" + key), "应该包含锁的统计信息");
            assertEquals(1, stats.get("test-lock:" + key).getAcquireCount(), "获取次数应该为1");
            assertEquals(2, stats.get("test-lock:" + key).getFailCount(), "失败次数应该为2");
        });
    }

    @Test
    void testLock() throws InterruptedException {
        String key = "test3";
        testLockService.lockMethod(key);

        // 验证锁指标
        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        assertTrue(stats.containsKey("test-lock:" + key), "应该包含锁的统计信息");
        assertEquals(1, stats.get("test-lock:" + key).getAcquireCount(), "获取次数应该为1");
    }

    @Test
    void testMultiLock() throws InterruptedException {
        String productId = "product1";
        String warehouseId = "warehouse1";
        testLockService.multiLockMethod(productId, warehouseId);

        // 验证锁指标
        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        String multiLockKey = "test-lock:" + productId + ",test-lock:" + warehouseId;
        assertTrue(stats.containsKey(multiLockKey), "应该包含联锁的统计信息");
        assertEquals(1, stats.get(multiLockKey).getAcquireCount(), "获取次数应该为1");
    }

    @Test
    void testReadWriteLock() throws InterruptedException {
        String resourceId = "resource1";

        // 先获取读锁
        AtomicBoolean readLockAcquired = new AtomicBoolean(false);
        CountDownLatch readLatch = new CountDownLatch(1);

        executorService.submit(() -> {
            try {
                testLockService.readResource(resourceId);
                readLockAcquired.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                readLatch.countDown();
            }
        });

        readLatch.await(2, TimeUnit.SECONDS);
        assertTrue(readLockAcquired.get(), "应该能够获取读锁");

        // 另一个读锁应该可以获取
        AtomicBoolean anotherReadLockAcquired = new AtomicBoolean(false);
        CountDownLatch anotherReadLatch = new CountDownLatch(1);

        executorService.submit(() -> {
            try {
                testLockService.readResource(resourceId);
                anotherReadLockAcquired.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                anotherReadLatch.countDown();
            }
        });

        anotherReadLatch.await(2, TimeUnit.SECONDS);
        assertTrue(anotherReadLockAcquired.get(), "应该能够获取另一个读锁");

        // 写锁应该无法获取（会被阻塞）
        AtomicBoolean writeLockAcquired = new AtomicBoolean(false);

        executorService.submit(() -> {
            try {
                testLockService.updateResource(resourceId, "data");
                writeLockAcquired.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 等待一段时间，写锁应该无法获取
        Thread.sleep(500);
        assertFalse(writeLockAcquired.get(), "存在读锁时不应该能够获取写锁");
    }

    @Test
    void testSpELExpression() throws InterruptedException {
        String orderId = "order123";
        int amount = 100;
        testLockService.spELLockMethod(orderId, amount);

        // 验证锁指标
        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        String lockKey = "order:" + orderId + ":" + amount;
        assertTrue(stats.containsKey(lockKey), "应该包含带SpEL表达式的锁的统计信息");
        assertEquals(1, stats.get(lockKey).getAcquireCount(), "获取次数应该为1");
    }

    /**
     * 测试服务类
     */
    @Slf4j
    @Service
    public static class TestLockService {

        @Lock(prefix = "test-lock", key = "#key", type = Lock.LockType.TRY_LOCK, waitTime = 1000, leaseTime = 2000)
        public void tryLockMethod(String key) throws InterruptedException {
            log.info("执行tryLock方法，key: {}", key);
            Thread.sleep(100); // 模拟业务处理
        }

        @Lock(prefix = "test-lock", key = "#key", type = Lock.LockType.LOCK)
        public void lockMethod(String key) throws InterruptedException {
            log.info("执行lock方法，key: {}", key);
            Thread.sleep(100); // 模拟业务处理
        }

        @Lock(prefix = "test-lock", keys = { "#productId", "#warehouseId" }, type = Lock.LockType.MULTI_LOCK)
        public void multiLockMethod(String productId, String warehouseId) throws InterruptedException {
            log.info("执行multiLock方法，productId: {}, warehouseId: {}", productId, warehouseId);
            Thread.sleep(100); // 模拟业务处理
        }

        @Lock(prefix = "order", key = "#orderId + ':' + #amount", type = Lock.LockType.TRY_LOCK, waitTime = 1000, leaseTime = 2000)
        public void spELLockMethod(String orderId, int amount) throws InterruptedException {
            log.info("执行spELLock方法，orderId: {}, amount: {}", orderId, amount);
            Thread.sleep(100); // 模拟业务处理
        }

        @Lock(prefix = "resource", key = "#resourceId", mode = Lock.LockMode.READ)
        public String readResource(String resourceId) throws InterruptedException {
            log.info("开始读取资源: {}", resourceId);
            Thread.sleep(500); // 模拟读取操作
            log.info("读取资源完成: {}", resourceId);
            return "Resource data: " + resourceId;
        }

        @Lock(prefix = "resource", key = "#resourceId", mode = Lock.LockMode.WRITE)
        public void updateResource(String resourceId, String data) throws InterruptedException {
            log.info("开始更新资源: {}, 数据: {}", resourceId, data);
            Thread.sleep(500); // 模拟更新操作
            log.info("更新资源完成: {}", resourceId);
        }
    }
}