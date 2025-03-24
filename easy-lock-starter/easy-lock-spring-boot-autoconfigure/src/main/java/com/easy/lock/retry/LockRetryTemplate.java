package com.easy.lock.retry;

import com.easy.lock.exception.LockAcquireFailedException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * 锁重试模板，用于在获取锁失败时进行重试
 */
@Slf4j
public class LockRetryTemplate {

    /**
     * 执行带重试的操作
     *
     * @param callable    要执行的操作
     * @param retryConfig 重试配置
     * @param <T>         返回类型
     * @return 操作结果
     * @throws LockAcquireFailedException 所有重试都失败后抛出异常
     */
    public static <T> T execute(Callable<T> callable, RetryConfig retryConfig) throws LockAcquireFailedException {
        int retryCount = 0;
        long startTime = System.currentTimeMillis();
        Exception lastException = null;

        do {
            try {
                T result = callable.call();
                if (retryConfig.getResultPredicate().test(result)) {
                    if (retryCount > 0) {
                        log.debug("重试成功, 重试次数: {}, 耗时: {}ms", retryCount, System.currentTimeMillis() - startTime);
                    }
                    return result;
                }
            } catch (Exception e) {
                lastException = e;
                log.debug("执行失败, 异常: {}", e.getMessage());
            }

            retryCount++;
            if (retryCount <= retryConfig.getMaxRetries()) {
                try {
                    long sleepTime = retryConfig.getBackoffStrategy().getSleepTime(retryCount);
                    log.debug("第 {} 次重试失败, 休眠 {}ms 后重试", retryCount, sleepTime);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LockAcquireFailedException("重试被中断", e);
                }
            }
        } while (retryCount <= retryConfig.getMaxRetries());

        String errorMsg = String.format("重试 %d 次后仍然失败，总耗时: %dms",
                retryConfig.getMaxRetries(), System.currentTimeMillis() - startTime);
        log.debug(errorMsg);

        if (lastException != null) {
            throw new LockAcquireFailedException(errorMsg, lastException);
        } else {
            throw new LockAcquireFailedException(errorMsg);
        }
    }

    /**
     * 重试配置
     */
    @Data
    @Builder
    public static class RetryConfig {
        /**
         * 最大重试次数
         */
        private final int maxRetries;

        /**
         * 重试间隔策略
         */
        private final BackoffStrategy backoffStrategy;

        /**
         * 结果断言，用于判断结果是否成功
         */
        private final Predicate<Object> resultPredicate;

        /**
         * 创建默认配置
         *
         * @return 默认配置
         */
        public static RetryConfig createDefault() {
            return RetryConfig.builder()
                    .maxRetries(3)
                    .backoffStrategy(new FixedBackoffStrategy(1000))
                    .resultPredicate(result -> result != null && (!(result instanceof Boolean) || (Boolean) result))
                    .build();
        }
    }

    /**
     * 重试间隔策略接口
     */
    public interface BackoffStrategy {
        /**
         * 获取下一次重试的等待时间
         *
         * @param retryCount 当前重试次数
         * @return 等待时间（毫秒）
         */
        long getSleepTime(int retryCount);
    }

    /**
     * 固定间隔重试策略
     */
    public static class FixedBackoffStrategy implements BackoffStrategy {
        private final long sleepTime;

        public FixedBackoffStrategy(long sleepTime) {
            this.sleepTime = sleepTime;
        }

        @Override
        public long getSleepTime(int retryCount) {
            return sleepTime;
        }
    }

    /**
     * 指数退避重试策略
     */
    public static class ExponentialBackoffStrategy implements BackoffStrategy {
        private final long initialSleepTime;
        private final double factor;
        private final long maxSleepTime;

        public ExponentialBackoffStrategy(long initialSleepTime, double factor, long maxSleepTime) {
            this.initialSleepTime = initialSleepTime;
            this.factor = factor;
            this.maxSleepTime = maxSleepTime;
        }

        @Override
        public long getSleepTime(int retryCount) {
            double exponentialFactor = Math.pow(factor, retryCount - 1);
            long sleepTime = (long) (initialSleepTime * exponentialFactor);
            return Math.min(sleepTime, maxSleepTime);
        }
    }
}