package com.easy.lock.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.easy.lock.AbstractIntegrationTest;
import com.easy.lock.api.RedisLock;

import lombok.extern.slf4j.Slf4j;

/**
 * 锁性能测试
 * 注意：这些测试可能需要较长时间运行，建议在CI/CD流水线中使用特定标签运行
 */
@Slf4j
@Tag("performance")
class LockPerformanceTest extends AbstractIntegrationTest {

    @Autowired
    private RedisLock redisLock;

    private ExecutorService executorService;
    private final List<String> testKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(20);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
        for (String key : testKeys) {
            try {
                redisLock.unlock(key);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void testLockPerformance() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 100;
        String lockKey = "perf:test:" + UUID.randomUUID().toString();
        testKeys.add(lockKey);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 创建并启动线程
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪

                    for (int j = 0; j < iterationsPerThread; j++) {
                        try {
                            boolean locked = redisLock.tryLock(lockKey, 1);
                            if (locked) {
                                try {
                                    // 模拟短暂的业务处理
                                    Thread.sleep(5);
                                    successCount.incrementAndGet();
                                } finally {
                                    redisLock.unlock(lockKey);
                                }
                            } else {
                                failCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.error("锁操作异常", e);
                            failCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 记录开始时间
        long startTime = System.currentTimeMillis();
        startLatch.countDown(); // 所有线程开始执行

        // 等待所有线程完成
        endLatch.await(60, TimeUnit.SECONDS);

        // 记录结束时间
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 计算统计信息
        int totalOperations = threadCount * iterationsPerThread;
        int completedOperations = successCount.get() + failCount.get();
        double operationsPerSecond = (double) completedOperations / (duration / 1000.0);
        double successRate = (double) successCount.get() / completedOperations;

        // 输出性能测试结果
        log.info("性能测试结果:");
        log.info("总操作数: {}", totalOperations);
        log.info("完成操作数: {}", completedOperations);
        log.info("成功操作数: {}", successCount.get());
        log.info("失败操作数: {}", failCount.get());
        log.info("总耗时: {}ms", duration);
        log.info("每秒操作数: {}", String.format("%.2f", operationsPerSecond));
        log.info("成功率: {}%", String.format("%.2f", successRate * 100));
    }

    @Test
    void testReadWriteLockPerformance() throws InterruptedException {
        int readerThreads = 8;
        int writerThreads = 2;
        int iterationsPerThread = 50;
        String lockKey = "perf:rwlock:" + UUID.randomUUID().toString();
        testKeys.add(lockKey);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(readerThreads + writerThreads);
        AtomicInteger readSuccessCount = new AtomicInteger(0);
        AtomicInteger readFailCount = new AtomicInteger(0);
        AtomicInteger writeSuccessCount = new AtomicInteger(0);
        AtomicInteger writeFailCount = new AtomicInteger(0);

        // 创建读线程
        for (int i = 0; i < readerThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < iterationsPerThread; j++) {
                        try {
                            boolean locked = redisLock.tryReadLock(lockKey, 500, 1000, TimeUnit.MILLISECONDS);
                            if (locked) {
                                try {
                                    // 模拟读操作
                                    Thread.sleep(10);
                                    readSuccessCount.incrementAndGet();
                                } finally {
                                    redisLock.readLock(lockKey).unlock();
                                }
                            } else {
                                readFailCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.error("读锁操作异常", e);
                            readFailCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 创建写线程
        for (int i = 0; i < writerThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < iterationsPerThread; j++) {
                        try {
                            boolean locked = redisLock.tryWriteLock(lockKey, 500, 1000, TimeUnit.MILLISECONDS);
                            if (locked) {
                                try {
                                    // 模拟写操作
                                    Thread.sleep(50);
                                    writeSuccessCount.incrementAndGet();
                                } finally {
                                    redisLock.writeLock(lockKey).unlock();
                                }
                            } else {
                                writeFailCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.error("写锁操作异常", e);
                            writeFailCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 记录开始时间
        long startTime = System.currentTimeMillis();
        startLatch.countDown();

        // 等待所有线程完成
        endLatch.await(60, TimeUnit.SECONDS);

        // 记录结束时间
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 计算统计信息
        int totalReadOperations = readerThreads * iterationsPerThread;
        int totalWriteOperations = writerThreads * iterationsPerThread;
        int completedReadOperations = readSuccessCount.get() + readFailCount.get();
        int completedWriteOperations = writeSuccessCount.get() + writeFailCount.get();
        double readOperationsPerSecond = (double) completedReadOperations / (duration / 1000.0);
        double writeOperationsPerSecond = (double) completedWriteOperations / (duration / 1000.0);
        double readSuccessRate = (double) readSuccessCount.get() / completedReadOperations;
        double writeSuccessRate = (double) writeSuccessCount.get() / completedWriteOperations;

        // 输出性能测试结果
        log.info("读写锁性能测试结果:");
        log.info("总读操作数: {}", totalReadOperations);
        log.info("完成读操作数: {}", completedReadOperations);
        log.info("成功读操作数: {}", readSuccessCount.get());
        log.info("失败读操作数: {}", readFailCount.get());
        log.info("总写操作数: {}", totalWriteOperations);
        log.info("完成写操作数: {}", completedWriteOperations);
        log.info("成功写操作数: {}", writeSuccessCount.get());
        log.info("失败写操作数: {}", writeFailCount.get());
        log.info("总耗时: {}ms", duration);
        log.info("每秒读操作数: {}", String.format("%.2f", readOperationsPerSecond));
        log.info("每秒写操作数: {}", String.format("%.2f", writeOperationsPerSecond));
        log.info("读成功率: {}%", String.format("%.2f", readSuccessRate * 100));
        log.info("写成功率: {}%", String.format("%.2f", writeSuccessRate * 100));
    }
}