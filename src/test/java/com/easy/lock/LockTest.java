package com.easy.lock;

import com.easy.lock.service.LockTestService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@SpringBootTest
public class LockTest {

    @Autowired
    private LockTestService lockTestService;
    
    private ExecutorService executorService;

    @AfterEach
    public void cleanup() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

    /**
     * 测试tryLock方式
     */
    @Test
    public void testTryLock() throws InterruptedException {
        int threadCount = 10;
        executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 同时启动10个线程
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    lockTestService.tryLockMethod("TEST_KEY");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("获取锁失败: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        log.info("成功获取锁的次数: {}", successCount.get());
        assert successCount.get() < threadCount; // 由于锁的存在，不是所有线程都能成功
    }

    /**
     * 测试永久锁
     */
    @Test
    public void testLock() throws InterruptedException {
        int threadCount = 5;
        executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger counter = new AtomicInteger(0);

        // 同时启动5个线程
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    lockTestService.lockMethod("TEST_KEY");
                    counter.incrementAndGet();
                } catch (InterruptedException e) {
                    log.error("执行被中断: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("执行异常: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        log.info("完成处理的线程数: {}", counter.get());
        assert counter.get() == threadCount; // 所有线程最终都能完成
    }

    /**
     * 测试联锁
     */
    @Test
    public void testMultiLock() throws InterruptedException {
        int threadCount = 5;
        executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 同时启动5个线程，尝试获取多个资源的锁
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.execute(() -> {
                try {
                    lockTestService.multiLockMethod("PRODUCT_" + index, "WAREHOUSE_" + index);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    log.error("执行被中断: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("获取联锁失败: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        log.info("成功获取联锁的次数: {}", successCount.get());
        assert successCount.get() == threadCount; // 每个线程操作不同的资源，应该都能成功
    }

    /**
     * 测试带参数的SpEL表达式锁
     */
    @Test
    public void testSpELLock() throws InterruptedException {
        int threadCount = 5;
        executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 同时启动5个线程，使用相同的订单ID
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    lockTestService.spELLockMethod("ORDER_123", 100);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    log.error("执行被中断: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("获取锁失败: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        log.info("成功处理订单的线程数: {}", successCount.get());
        assert successCount.get() == 1; // 只有一个线程能成功处理相同的订单
    }
} 