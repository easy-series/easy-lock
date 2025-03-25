package com.caoyixin.lock.test;

import com.caoyixin.lock.core.LockExecutor;
import com.caoyixin.lock.core.LockInfo;
import com.caoyixin.lock.core.LockTemplate;
import com.caoyixin.lock.redisson.executor.RedissonLockExecutor;
import com.caoyixin.lock.test.config.TestConfiguration;
import com.caoyixin.lock.test.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分布式锁集成测试类 - 使用Redisson实现
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestConfiguration.class)
public class LockIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(LockIntegrationTest.class);

    @Autowired
    private LockTemplate lockTemplate;

    @Autowired
    private LockExecutor lockExecutor;

    @Autowired
    private StockService stockService;

    @BeforeEach
    public void setUp() {
        // 确保使用的是Redisson执行器
        assertNotNull(lockExecutor);
        assertTrue(lockExecutor instanceof RedissonLockExecutor, "锁执行器应该是RedissonLockExecutor类型");

        // 重置库存服务状态
        stockService.reset();
    }

    /**
     * 测试基本锁获取和释放
     */
    @Test
    public void testBasicLock() {
        String lockKey = "test:basic:lock";
        long expireTime = 30000;
        long waitTime = 3000;

        // 获取锁
        LockInfo lockInfo = lockTemplate.lock(lockKey, expireTime, waitTime);
        assertNotNull(lockInfo, "应该成功获取锁");
        assertEquals(lockKey, lockInfo.getKey(), "锁键应该匹配");

        // 验证锁已获取
        assertTrue(lockExecutor.isLocked(lockKey), "锁应该已被获取");

        // 释放锁
        boolean released = lockTemplate.releaseLock(lockInfo);
        assertTrue(released, "应该成功释放锁");

        // 验证锁已释放
        assertFalse(lockExecutor.isLocked(lockKey), "锁应该已被释放");
    }

    /**
     * 测试使用Lambda表达式执行带锁操作
     */
    @Test
    public void testExecuteWithLock() {
        String lockKey = "test:lambda:lock";
        long expireTime = 5000;
        long waitTime = 1000;

        // 执行带锁操作
        String result = lockTemplate.executeWithLock(lockKey, expireTime, waitTime, () -> {
            // 验证在Lambda内部锁已获取
            assertTrue(lockExecutor.isLocked(lockKey), "锁应该在Lambda执行期间被获取");
            return "操作成功";
        });

        assertEquals("操作成功", result, "Lambda表达式应该成功执行并返回结果");

        // 验证锁已释放
        assertFalse(lockExecutor.isLocked(lockKey), "锁应该在Lambda执行后被释放");
    }

    /**
     * 测试获取锁失败时返回默认值
     */
    @Test
    public void testExecuteWithLockDefaultValue() {
        String lockKey = "test:defaultvalue:lock";
        long expireTime = 5000;
        long waitTime = 1000;
        String defaultValue = "默认值";

        // 先获取锁，不释放
        LockInfo firstLock = lockTemplate.lock(lockKey, expireTime, 1000);
        assertNotNull(firstLock, "应该成功获取第一个锁");

        try {
            // 尝试使用不同线程执行带锁操作，应该返回默认值
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                return lockTemplate.executeWithLockReturnDefault(lockKey, expireTime, waitTime, () -> {
                    fail("不应该执行此代码块");
                    return "不应该返回的值";
                }, defaultValue);
            });

            String result = future.get(waitTime + 500, TimeUnit.MILLISECONDS);
            assertEquals(defaultValue, result, "应该返回默认值");
        } catch (Exception e) {
            fail("测试过程中发生异常: " + e.getMessage());
        } finally {
            // 释放第一个锁
            lockTemplate.releaseLock(firstLock);
        }
    }

    /**
     * 测试注解锁功能
     */
    @Test
    public void testAnnotatedLock() {
        String productId = "product-1";
        int initialStock = 10;
        int decrementAmount = 3;

        // 初始化库存
        stockService.initStock(productId, initialStock);

        // 使用注解锁减少库存
        boolean result = stockService.decrementStock(productId, decrementAmount);

        assertTrue(result, "应该成功减少库存");
        assertEquals(initialStock - decrementAmount, stockService.getStock(productId), "库存应该正确减少");
        assertEquals(1, stockService.getDecrementCallCount(), "减少库存方法应该被调用1次");
    }

    /**
     * 测试SpEL表达式锁
     */
    @Test
    public void testSpELLock() {
        String fromProduct = "product-source";
        String toProduct = "product-target";
        int initialStock = 20;
        int transferAmount = 5;

        // 初始化源产品库存
        stockService.initStock(fromProduct, initialStock);

        // 使用SpEL表达式锁转移库存
        boolean result = stockService.transferStock(fromProduct, toProduct, transferAmount);

        assertTrue(result, "应该成功转移库存");
        assertEquals(initialStock - transferAmount, stockService.getStock(fromProduct), "源产品库存应该正确减少");
        assertEquals(transferAmount, stockService.getStock(toProduct), "目标产品库存应该正确增加");
    }

    /**
     * 测试减少库存场景
     */
    @Test
    public void testStockReduction() {
        String productId = "product-test";
        int initialStock = 100;

        // 初始化库存
        stockService.initStock(productId, initialStock);

        // 使用编程式锁减少库存
        boolean result1 = stockService.decrementStockWithProgrammaticLock(productId, 30);
        assertTrue(result1, "首次减少库存应该成功");
        assertEquals(70, stockService.getStock(productId), "库存应该减少到70");

        // 再次减少库存
        boolean result2 = stockService.decrementStockWithProgrammaticLock(productId, 50);
        assertTrue(result2, "再次减少库存应该成功");
        assertEquals(20, stockService.getStock(productId), "库存应该减少到20");

        // 超出可用库存
        boolean result3 = stockService.decrementStockWithProgrammaticLock(productId, 30);
        assertFalse(result3, "超出可用库存应该失败");
        assertEquals(20, stockService.getStock(productId), "库存应该保持不变");

        // 验证调用计数
        assertEquals(3, stockService.getDecrementCallCount(), "减少库存方法应该被调用3次");
    }

    /**
     * 测试并发减少库存
     */
    @Test
    public void testConcurrentStockReduction() throws InterruptedException, ExecutionException {
        String productId = "product-concurrent";
        int initialStock = 100;
        int threads = 10;
        int quantityPerThread = 10; // 每个线程减少10个

        // 初始化库存
        stockService.initStock(productId, initialStock);

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Future<Boolean>> results = new ArrayList<>();

        // 启动多个线程并发减少库存
        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                try {
                    return stockService.decrementStockWithProgrammaticLock(productId, quantityPerThread);
                } finally {
                    latch.countDown();
                }
            }));
        }

        // 等待所有线程完成
        latch.await(20, TimeUnit.SECONDS); // 增加等待时间，考虑真实环境网络延迟

        // 检查结果
        int successCount = 0;
        for (Future<Boolean> future : results) {
            if (future.get()) {
                successCount++;
            }
        }

        // 验证正确的库存减少
        assertEquals(threads, successCount, "所有线程都应该成功减少库存");
        assertEquals(0, stockService.getStock(productId), "最终库存应该为0");
        assertEquals(threads, stockService.getDecrementCallCount(), "减少库存方法应该被调用" + threads + "次");

        executor.shutdown();
    }

    /**
     * 测试锁重入
     */
    @Test
    public void testLockReentrant() {
        String lockKey = "test:reentrant:lock";

        // 获取锁
        LockInfo lockInfo = lockTemplate.lock(lockKey, 30000, 1000);
        assertNotNull(lockInfo, "应该成功获取锁");

        try {
            // 确认锁已获取
            assertTrue(lockExecutor.isLocked(lockKey), "锁应该已经被获取");

            // 再次获取相同的锁（重入）
            LockInfo reentrantLock = lockTemplate.lock(lockKey, 30000, 1000);
            assertNotNull(reentrantLock, "应该成功获取重入锁");

            // 再次重入
            LockInfo reentrantLock2 = lockTemplate.lock(lockKey, 30000, 1000);
            assertNotNull(reentrantLock2, "应该成功获取第二次重入锁");

            // 释放第二次重入锁
            assertTrue(lockTemplate.releaseLock(reentrantLock2), "应该成功释放第二次重入锁");

            // 释放第一次重入锁
            assertTrue(lockTemplate.releaseLock(reentrantLock), "应该成功释放第一次重入锁");

            // 锁应该仍然存在，因为主锁还未释放
            assertTrue(lockExecutor.isLocked(lockKey), "主锁释放前，锁应该仍然存在");

        } finally {
            // 释放初始锁
            assertTrue(lockTemplate.releaseLock(lockInfo), "应该成功释放初始锁");
        }

        // 锁应该已完全释放
        assertFalse(lockExecutor.isLocked(lockKey), "所有锁释放后，锁应该不再存在");
        LockInfo newLock = lockTemplate.lock(lockKey, 1000, 1000);
        assertNotNull(newLock, "应该能够重新获取锁");
        lockTemplate.releaseLock(newLock);
    }

    /**
     * 测试锁超时
     */
    @Test
    public void testLockTimeout() throws InterruptedException {
        String lockKey = "test:timeout:lock";

        // 在一个线程中获取锁并长时间持有
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                LockInfo lockInfo = lockTemplate.lock(lockKey, 30000, 3000);
                assertNotNull(lockInfo, "线程1应该成功获取锁");
                assertTrue(lockExecutor.isLocked(lockKey), "锁应该已被获取");
                lockAcquiredLatch.countDown(); // 通知主线程锁已获取

                // 持有锁一段时间
                Thread.sleep(3000);

                lockTemplate.releaseLock(lockInfo);
            } catch (Exception e) {
                logger.error("持有锁线程发生异常", e);
            }
        });

        // 等待线程获取锁
        assertTrue(lockAcquiredLatch.await(5, TimeUnit.SECONDS), "应该在预定时间内获取到锁");

        // 尝试获取已被持有的锁，应该超时
        LockInfo lockInfo = lockTemplate.lock(lockKey, 5000, 500);
        assertNull(lockInfo, "应该未能获取已被持有的锁");

        // 睡眠足够长时间，让锁被释放
        Thread.sleep(4000);

        // 现在应该能获取锁了
        LockInfo newLockInfo = lockTemplate.lock(lockKey, 5000, 1000);
        assertNotNull(newLockInfo, "应该能获取到已被释放的锁");
        lockTemplate.releaseLock(newLockInfo);

        executor.shutdown();
    }
}