package com.easy.lock.sample.performance;

import com.easy.lock.sample.RedisTestConfiguration;
import com.easy.lock.sample.TestRedisLockConfiguration;
import com.easy.lock.sample.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁性能测试
 * 注意：这个测试类主要用于性能比较，不作为正常单元测试运行
 * 注意：此测试需要Redis服务运行在localhost:6379
 */
@SpringBootTest
@Slf4j
@Import({ RedisTestConfiguration.class, TestRedisLockConfiguration.class })
public class LockPerformanceTest {

    @Autowired
    private OrderService orderService;

    private static final int THREAD_COUNT = 10;
    private static final int ITERATIONS_PER_THREAD = 5;

    /**
     * 测试基本锁性能
     */
    @Test
    @DisplayName("基本锁性能测试")
    public void testBasicLockPerformance() throws Exception {
        // 使用唯一的订单ID
        String orderId = "perf-" + UUID.randomUUID().toString();

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // 性能统计
        StopWatch stopWatch = new StopWatch("基本锁性能测试");
        stopWatch.start("基本锁并发测试");

        List<CompletableFuture<Long>> futures = new ArrayList<>();

        // 并发执行多个线程，每个线程执行多次请求
        for (int i = 0; i < THREAD_COUNT; i++) {
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                long totalTime = 0;

                for (int j = 0; j < ITERATIONS_PER_THREAD; j++) {
                    String threadOrderId = orderId + "-" + j;
                    long startTime = System.currentTimeMillis();

                    try {
                        orderService.processOrder(threadOrderId);
                        long endTime = System.currentTimeMillis();
                        totalTime += (endTime - startTime);
                    } catch (Exception e) {
                        log.error("处理订单出错: {}", e.getMessage());
                    }
                }

                return totalTime;
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        stopWatch.stop();

        // 计算平均时间
        long totalTime = 0;
        for (CompletableFuture<Long> future : futures) {
            totalTime += future.get();
        }

        double avgTimePerOperation = (double) totalTime / (THREAD_COUNT * ITERATIONS_PER_THREAD);

        // 记录性能结果
        log.info("======== 基本锁性能测试结果 ========");
        log.info("总线程数: {}", THREAD_COUNT);
        log.info("每线程操作次数: {}", ITERATIONS_PER_THREAD);
        log.info("总操作次数: {}", THREAD_COUNT * ITERATIONS_PER_THREAD);
        log.info("总耗时: {}ms", stopWatch.getTotalTimeMillis());
        log.info("平均每次操作耗时: {}ms", avgTimePerOperation);
        log.info("====================================");

        // 关闭线程池
        executor.shutdown();
    }

    /**
     * 测试重试锁性能
     */
    @Test
    @DisplayName("重试锁性能测试")
    public void testRetryLockPerformance() throws Exception {
        // 使用唯一的订单ID
        String orderId = "retry-perf-" + UUID.randomUUID().toString();

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // 性能统计
        StopWatch stopWatch = new StopWatch("重试锁性能测试");
        stopWatch.start("重试锁并发测试");

        List<CompletableFuture<Long>> futures = new ArrayList<>();

        // 并发执行多个线程，每个线程执行多次请求
        for (int i = 0; i < THREAD_COUNT; i++) {
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                long totalTime = 0;

                for (int j = 0; j < ITERATIONS_PER_THREAD; j++) {
                    String threadOrderId = orderId + "-" + j;
                    long startTime = System.currentTimeMillis();

                    try {
                        orderService.processOrderWithRetry(threadOrderId);
                        long endTime = System.currentTimeMillis();
                        totalTime += (endTime - startTime);
                    } catch (Exception e) {
                        log.error("处理订单出错(重试): {}", e.getMessage());
                    }
                }

                return totalTime;
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        stopWatch.stop();

        // 计算平均时间
        long totalTime = 0;
        for (CompletableFuture<Long> future : futures) {
            totalTime += future.get();
        }

        double avgTimePerOperation = (double) totalTime / (THREAD_COUNT * ITERATIONS_PER_THREAD);

        // 记录性能结果
        log.info("======== 重试锁性能测试结果 ========");
        log.info("总线程数: {}", THREAD_COUNT);
        log.info("每线程操作次数: {}", ITERATIONS_PER_THREAD);
        log.info("总操作次数: {}", THREAD_COUNT * ITERATIONS_PER_THREAD);
        log.info("总耗时: {}ms", stopWatch.getTotalTimeMillis());
        log.info("平均每次操作耗时: {}ms", avgTimePerOperation);
        log.info("====================================");

        // 关闭线程池
        executor.shutdown();
    }

    /**
     * 测试重入锁性能
     */
    @Test
    @DisplayName("重入锁性能测试")
    public void testReentrantLockPerformance() throws Exception {
        // 使用唯一的订单ID
        String orderId = "reentrant-perf-" + UUID.randomUUID().toString();

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // 性能统计
        StopWatch stopWatch = new StopWatch("重入锁性能测试");
        stopWatch.start("重入锁并发测试");

        List<CompletableFuture<Long>> futures = new ArrayList<>();

        // 并发执行多个线程，每个线程执行多次请求
        for (int i = 0; i < THREAD_COUNT; i++) {
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                long totalTime = 0;

                for (int j = 0; j < ITERATIONS_PER_THREAD; j++) {
                    String threadOrderId = orderId + "-" + j;
                    long startTime = System.currentTimeMillis();

                    try {
                        orderService.processOrderWithReentrant(threadOrderId);
                        long endTime = System.currentTimeMillis();
                        totalTime += (endTime - startTime);
                    } catch (Exception e) {
                        log.error("处理订单出错(重入): {}", e.getMessage());
                    }
                }

                return totalTime;
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        stopWatch.stop();

        // 计算平均时间
        long totalTime = 0;
        for (CompletableFuture<Long> future : futures) {
            totalTime += future.get();
        }

        double avgTimePerOperation = (double) totalTime / (THREAD_COUNT * ITERATIONS_PER_THREAD);

        // 记录性能结果
        log.info("======== 重入锁性能测试结果 ========");
        log.info("总线程数: {}", THREAD_COUNT);
        log.info("每线程操作次数: {}", ITERATIONS_PER_THREAD);
        log.info("总操作次数: {}", THREAD_COUNT * ITERATIONS_PER_THREAD);
        log.info("总耗时: {}ms", stopWatch.getTotalTimeMillis());
        log.info("平均每次操作耗时: {}ms", avgTimePerOperation);
        log.info("====================================");

        // 关闭线程池
        executor.shutdown();
    }

    /**
     * 比较三种锁的性能
     */
    @Test
    @DisplayName("三种锁性能比较")
    public void testCompareLockPerformance() throws Exception {
        // 基础锁测试
        StopWatch basicStopWatch = runPerformanceTest("basic",
                (orderId) -> orderService.processOrder(orderId));

        // 重试锁测试
        StopWatch retryStopWatch = runPerformanceTest("retry",
                (orderId) -> orderService.processOrderWithRetry(orderId));

        // 重入锁测试
        StopWatch reentrantStopWatch = runPerformanceTest("reentrant",
                (orderId) -> orderService.processOrderWithReentrant(orderId));

        // 输出比较结果
        log.info("======== 锁性能比较结果 ========");
        log.info("基础锁总耗时: {}ms, 平均耗时: {}ms",
                basicStopWatch.getTotalTimeMillis(),
                basicStopWatch.getTotalTimeMillis() / (double) (THREAD_COUNT * ITERATIONS_PER_THREAD));

        log.info("重试锁总耗时: {}ms, 平均耗时: {}ms",
                retryStopWatch.getTotalTimeMillis(),
                retryStopWatch.getTotalTimeMillis() / (double) (THREAD_COUNT * ITERATIONS_PER_THREAD));

        log.info("重入锁总耗时: {}ms, 平均耗时: {}ms",
                reentrantStopWatch.getTotalTimeMillis(),
                reentrantStopWatch.getTotalTimeMillis() / (double) (THREAD_COUNT * ITERATIONS_PER_THREAD));

        log.info("=================================");
    }

    /**
     * 运行性能测试的通用方法
     */
    private StopWatch runPerformanceTest(String testName, OrderProcessor processor) throws Exception {
        // 使用唯一的订单ID前缀
        String orderIdPrefix = testName + "-perf-" + UUID.randomUUID().toString();

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // 性能统计
        StopWatch stopWatch = new StopWatch(testName + "锁性能测试");
        stopWatch.start(testName + "锁并发测试");

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 并发执行多个线程
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < ITERATIONS_PER_THREAD; j++) {
                    String threadOrderId = orderIdPrefix + "-" + threadIndex + "-" + j;

                    try {
                        processor.process(threadOrderId);
                    } catch (Exception e) {
                        log.error("处理订单出错({}): {}", testName, e.getMessage());
                    }
                }
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        stopWatch.stop();

        // 关闭线程池
        executor.shutdown();

        return stopWatch;
    }

    /**
     * 订单处理器函数式接口
     */
    @FunctionalInterface
    private interface OrderProcessor {
        String process(String orderId);
    }
}