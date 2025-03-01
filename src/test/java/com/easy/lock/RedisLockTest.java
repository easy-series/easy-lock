package com.easy.lock;

import com.easy.lock.api.RedisLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
public class RedisLockTest {

    @Autowired
    private RedissonClient redissonClient;

    private RedisLock redisLock;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        redisLock = new RedisLock(redissonClient);
    }

    @AfterEach
    void cleanup() {
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
     * 测试基本的 tryLock 功能
     */
    @Test
    void testTryLock() throws InterruptedException {
        String key = "test:tryLock";
        // 第一次获取锁应该成功
        assertTrue(redisLock.tryLock(key, 5));
        
        // 在另一个线程中尝试获取同一个锁应该失败
        ExecutorService singleThread = Executors.newSingleThreadExecutor();
        try {
            assertFalse(singleThread.submit(() -> redisLock.tryLock(key, 5)).get());
        } catch (Exception e) {
            fail("测试过程中发生异常: " + e.getMessage());
        } finally {
            singleThread.shutdown();
            redisLock.unlock(key);
        }
    }

    /**
     * 测试并发情况下的 tryLock
     */
    @Test
    void testTryLockConcurrent() throws InterruptedException {
        int threadCount = 10;
        String key = "test:tryLockConcurrent";
        executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 同时启动10个线程竞争同一个锁
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    if (redisLock.tryLock(key, 1)) {
                        successCount.incrementAndGet();
                        Thread.sleep(100); // 模拟业务处理
                        redisLock.unlock(key);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        log.info("成功获取锁的次数: {}", successCount.get());
        // 由于是互斥锁，成功次数应该小于线程总数
        assertTrue(successCount.get() > 0 && successCount.get() < threadCount);
    }

    /**
     * 测试永久锁（自动续期）
     */
    @Test
    void testLock() throws InterruptedException {
        String key = "test:lock";
        AtomicInteger counter = new AtomicInteger(0);
        int threadCount = 5;
        executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 同时启动5个线程
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    redisLock.lock(key);
                    counter.incrementAndGet();
                    Thread.sleep(100); // 模拟业务处理
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    redisLock.unlock(key);
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(threadCount, counter.get(), "所有线程都应该能够执行完成");
    }

    /**
     * 测试联锁功能
     */
    @Test
    void testMultiLock() throws InterruptedException {
        List<String> keys = Arrays.asList("test:multiLock:1", "test:multiLock:2");
        AtomicInteger successCount = new AtomicInteger(0);
        int threadCount = 5;
        executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 同时启动5个线程尝试获取联锁
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    if (redisLock.multiLock(keys)) {
                        successCount.incrementAndGet();
                        Thread.sleep(100); // 模拟业务处理
                        redisLock.unMultiLock(keys);
                    }
                } catch (Exception e) {
                    log.error("获取联锁失败: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        log.info("成功获取联锁的次数: {}", successCount.get());
        // 由于是互斥的联锁，成功次数应该小于线程总数
        assertTrue(successCount.get() > 0 && successCount.get() < threadCount);
    }

    /**
     * 测试锁的自动释放
     */
    @Test
    void testLockExpiration() throws InterruptedException {
        String key = "test:lockExpiration";
        
        // 获取一个3秒后过期的锁
        assertTrue(redisLock.tryLock(key, 3));
        
        // 立即尝试获取锁应该失败
        assertFalse(redisLock.tryLock(key, 1));
        
        // 等待4秒（确保锁已经过期）
        Thread.sleep(4000);
        
        // 现在应该能获取到锁了
        assertTrue(redisLock.tryLock(key, 3));
        redisLock.unlock(key);
    }
} 