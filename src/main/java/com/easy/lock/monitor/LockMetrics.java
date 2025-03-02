package com.easy.lock.monitor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 锁的监控指标
 */
@Slf4j
@Component
public class LockMetrics {

    /**
     * 锁获取次数
     */
    private final Map<String, AtomicLong> lockAcquireCount = new ConcurrentHashMap<>();

    /**
     * 锁获取失败次数
     */
    private final Map<String, AtomicLong> lockFailCount = new ConcurrentHashMap<>();

    /**
     * 锁持有时间（毫秒）
     */
    private final Map<String, AtomicLong> lockHoldTime = new ConcurrentHashMap<>();

    /**
     * 锁等待时间（毫秒）
     */
    private final Map<String, AtomicLong> lockWaitTime = new ConcurrentHashMap<>();

    /**
     * 记录锁获取
     * 
     * @param lockKey 锁的key
     */
    public void recordLockAcquire(String lockKey) {
        lockAcquireCount.computeIfAbsent(lockKey, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 记录锁获取失败
     * 
     * @param lockKey 锁的key
     */
    public void recordLockFail(String lockKey) {
        lockFailCount.computeIfAbsent(lockKey, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 记录锁持有时间
     * 
     * @param lockKey  锁的key
     * @param holdTime 持有时间（毫秒）
     */
    public void recordLockHoldTime(String lockKey, long holdTime) {
        lockHoldTime.computeIfAbsent(lockKey, k -> new AtomicLong(0)).addAndGet(holdTime);
    }

    /**
     * 记录锁等待时间
     * 
     * @param lockKey  锁的key
     * @param waitTime 等待时间（毫秒）
     */
    public void recordLockWaitTime(String lockKey, long waitTime) {
        lockWaitTime.computeIfAbsent(lockKey, k -> new AtomicLong(0)).addAndGet(waitTime);
    }

    /**
     * 获取锁的统计信息
     * 
     * @return 锁的统计信息
     */
    public Map<String, LockStat> getLockStats() {
        Map<String, LockStat> stats = new ConcurrentHashMap<>();

        lockAcquireCount.forEach((key, count) -> {
            LockStat stat = new LockStat();
            stat.setLockKey(key);
            stat.setAcquireCount(count.get());
            stat.setFailCount(lockFailCount.getOrDefault(key, new AtomicLong(0)).get());
            stat.setHoldTime(lockHoldTime.getOrDefault(key, new AtomicLong(0)).get());
            stat.setWaitTime(lockWaitTime.getOrDefault(key, new AtomicLong(0)).get());
            stats.put(key, stat);
        });

        return stats;
    }

    /**
     * 清除锁的统计信息
     */
    public void clearStats() {
        lockAcquireCount.clear();
        lockFailCount.clear();
        lockHoldTime.clear();
        lockWaitTime.clear();
    }

    /**
     * 锁的统计信息
     */
    @Data
    public static class LockStat {
        /**
         * 锁的key
         */
        private String lockKey;

        /**
         * 获取次数
         */
        private long acquireCount;

        /**
         * 失败次数
         */
        private long failCount;

        /**
         * 持有时间（毫秒）
         */
        private long holdTime;

        /**
         * 等待时间（毫秒）
         */
        private long waitTime;

        /**
         * 获取成功率
         */
        public double getSuccessRate() {
            if (acquireCount + failCount == 0) {
                return 0;
            }
            return (double) acquireCount / (acquireCount + failCount);
        }

        /**
         * 获取平均持有时间
         */
        public double getAvgHoldTime() {
            if (acquireCount == 0) {
                return 0;
            }
            return (double) holdTime / acquireCount;
        }

        /**
         * 获取平均等待时间
         */
        public double getAvgWaitTime() {
            if (acquireCount + failCount == 0) {
                return 0;
            }
            return (double) waitTime / (acquireCount + failCount);
        }
    }
}