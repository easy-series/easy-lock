package com.caoyixin.lock.test;

import com.caoyixin.lock.core.LockInfo;
import com.caoyixin.lock.core.LockTemplate;
import com.caoyixin.lock.test.config.TestConfiguration;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分布式锁性能测试类
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestConfiguration.class)
public class LockPerformanceTest {

    private static final Logger logger = LoggerFactory.getLogger(LockPerformanceTest.class);

    @Autowired
    private LockTemplate lockTemplate;

    /**
     * 测试锁的获取和释放性能
     */
    @Test
    public void testLockPerformance() {
        int iterations = 1000; // 获取和释放锁的次数
        String key = "perf:lock:basic";

        // 预热
        for (int i = 0; i < 100; i++) {
            LockInfo lockInfo = lockTemplate.lock(key + i, 5000, 1000);
            lockTemplate.releaseLock(lockInfo);
        }

        // 计时获取与释放
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            LockInfo lockInfo = lockTemplate.lock(key + i, 5000, 1000);
            assertNotNull(lockInfo, "锁获取不应该失败");

            boolean released = lockTemplate.releaseLock(lockInfo);
            assertTrue(released, "锁释放不应该失败");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        double avgTimePerLock = (double) duration / iterations;
        logger.info("锁性能测试: {} 次操作, 总时间 {} ms, 平均每次操作耗时 {} ms",
                iterations, duration, avgTimePerLock);
    }

    /**
     * 测试并发性能
     */
    @Test
    public void testConcurrentPerformance() throws InterruptedException, ExecutionException {
        int threads = 10; // 线程数
        int iterationsPerThread = 100; // 每个线程的迭代次数

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threads);
        List<Future<Long>> results = new ArrayList<>();

        // 准备所有线程
        for (int t = 0; t < threads; t++) {
            final int threadNum = t;
            results.add(executorService.submit(() -> {
                // 等待开始信号
                startSignal.await();

                // 记录此线程的开始时间
                long threadStart = System.currentTimeMillis();

                // 执行锁操作
                for (int i = 0; i < iterationsPerThread; i++) {
                    String key = "perf:concurrent:" + threadNum + ":" + i;

                    try {
                        // 使用锁执行操作
                        lockTemplate.executeWithLock(key, 5000, 1000, () -> {
                            // 模拟短暂操作
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return null;
                        });
                    } catch (Exception e) {
                        logger.error("线程 {} 在迭代 {} 时发生异常", threadNum, i, e);
                    }
                }

                // 计算耗时
                long threadEnd = System.currentTimeMillis();
                long threadDuration = threadEnd - threadStart;

                // 完成信号
                doneSignal.countDown();

                return threadDuration;
            }));
        }

        // 开始计时并发布开始信号
        long startTime = System.currentTimeMillis();
        startSignal.countDown();

        // 等待所有线程完成
        doneSignal.await();
        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;

        // 计算统计信息
        List<Long> threadTimes = new ArrayList<>();
        for (Future<Long> result : results) {
            threadTimes.add(result.get());
        }

        long minTime = threadTimes.stream().min(Long::compare).orElse(0L);
        long maxTime = threadTimes.stream().max(Long::compare).orElse(0L);
        double avgTime = threadTimes.stream().mapToLong(Long::valueOf).average().orElse(0);

        // 计算每秒操作数
        int totalOperations = threads * iterationsPerThread;
        double operationsPerSecond = (double) totalOperations / (totalDuration / 1000.0);

        logger.info("并发性能测试结果:");
        logger.info("  线程数: {}", threads);
        logger.info("  每线程操作数: {}", iterationsPerThread);
        logger.info("  总操作数: {}", totalOperations);
        logger.info("  总耗时: {} ms", totalDuration);
        logger.info("  每秒操作数: {}", String.format("%.2f", operationsPerSecond));
        logger.info("  线程耗时统计: 最短 {} ms, 最长 {} ms, 平均 {} ms",
                minTime, maxTime, String.format("%.2f", avgTime));

        executorService.shutdown();
    }

    /**
     * 测试锁超时情况的性能
     */
    @Test
    public void testTimeoutPerformance() {
        int iterations = 100;
        String key = "perf:timeout";

        // 创建计数器
        AtomicInteger acquireSuccessCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);

        // 首先获取锁，但不释放它
        LockInfo holdLock = lockTemplate.lock(key, 10000, 1000);
        assertNotNull(holdLock, "应该成功获取持有锁");

        try {
            // 计时获取尝试
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < iterations; i++) {
                // 尝试获取已被持有的锁，应该超时
                LockInfo lockInfo = lockTemplate.lock(key, 5000, 200);

                if (lockInfo != null) {
                    acquireSuccessCount.incrementAndGet();
                    lockTemplate.releaseLock(lockInfo);
                } else {
                    timeoutCount.incrementAndGet();
                }
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            double avgTimePerAttempt = (double) duration / iterations;

            logger.info("锁超时性能测试: {} 次尝试, 成功 {}, 超时 {}, 总时间 {} ms, 平均每次尝试耗时 {} ms",
                    iterations, acquireSuccessCount.get(), timeoutCount.get(),
                    duration, avgTimePerAttempt);

            // 验证结果 - 大部分应该超时
            assertTrue(timeoutCount.get() > 0, "应该有锁获取超时");

        } finally {
            // 释放持有锁
            lockTemplate.releaseLock(holdLock);
        }
    }

    /**
     * 测试锁重入性能
     */
    @Test
    public void testReentrantPerformance() {
        int iterations = 1000;
        String key = "perf:reentrant";

        // 先获取锁
        LockInfo lockInfo = lockTemplate.lock(key, 30000, 1000);
        assertNotNull(lockInfo, "初始锁获取应该成功");

        try {
            // 计时重入操作
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < iterations; i++) {
                // 执行锁重入，应该成功
                lockTemplate.lock(key, 30000, 1000);
                assertEquals(i + 1, lockInfo.getReentrantCount(), "重入计数应该递增");
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            double avgTimePerReentry = (double) duration / iterations;

            logger.info("锁重入性能测试: {} 次重入, 总时间 {} ms, 平均每次重入耗时 {} ms",
                    iterations, duration, avgTimePerReentry);

            // 验证重入计数
            assertEquals(iterations, lockInfo.getReentrantCount(), "最终重入计数应该等于迭代次数");

        } finally {
            // 释放所有重入锁并最终释放锁
            for (int i = 0; i < lockInfo.getReentrantCount(); i++) {
                lockTemplate.releaseLock(lockInfo);
            }
            lockTemplate.releaseLock(lockInfo);
        }
    }

    /**
     * 测试不同锁键数量的性能影响
     */
    @Test
    public void testVariousKeyCountPerformance() throws InterruptedException {
        // 测试不同数量的锁键
        testKeysPerformance(1, "单一键"); // 所有线程竞争同一把锁
        testKeysPerformance(10, "少量键"); // 少量锁键
        testKeysPerformance(100, "中等键"); // 中等数量锁键
        testKeysPerformance(1000, "大量键"); // 大量锁键
    }

    /**
     * 辅助方法：测试指定数量锁键的性能
     */
    private void testKeysPerformance(int keyCount, String testName) throws InterruptedException {
        int threads = 20;
        int operationsPerThread = 50;

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);

        // 启动线程
        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // 每个线程执行多次操作
                    for (int i = 0; i < operationsPerThread; i++) {
                        // 选择一个锁键 (根据键数量分布)
                        int keyIndex = i % keyCount;
                        String key = "perf:keys:" + testName + ":" + keyIndex;

                        // 获取锁并执行操作
                        lockTemplate.executeWithLock(key, 5000, 1000, () -> {
                            // 模拟锁内操作
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return null;
                        });
                    }
                } catch (Exception e) {
                    logger.error("键测试中发生异常: " + testName, e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 开始所有线程
        startLatch.countDown();

        // 等待所有线程完成
        endLatch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 计算统计数据
        int totalOperations = threads * operationsPerThread;
        double operationsPerSecond = (double) totalOperations / (duration / 1000.0);

        logger.info("键数量性能测试 [{}]: {} 个键, {} 个线程, {} 次操作, 总时间 {} ms, 每秒 {} 次操作",
                testName, keyCount, threads, totalOperations, duration,
                String.format("%.2f", operationsPerSecond));

        executor.shutdown();
    }
}