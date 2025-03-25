package com.caoyixin.lock.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于Micrometer的锁监控实现
 *
 * @author caoyixin
 */
@Slf4j
public class MicrometerLockMetrics implements LockMetrics {

    private final MeterRegistry meterRegistry;

    /**
     * 当前活跃锁计数
     */
    private final ConcurrentMap<String, AtomicInteger> activeLockCountMap = new ConcurrentHashMap<>();

    public MicrometerLockMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordLockAttempt(String key, String name) {
        try {
            Counter.builder("cyx_lock_acquire_total")
                    .description("Total number of lock acquire attempts")
                    .tags(createTags(key, name))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("Failed to record lock acquire attempt metrics", e);
        }
    }

    @Override
    public void recordLockSuccess(String key, String name, long acquireTime) {
        try {
            // 记录获取锁耗时
            Timer.builder("cyx_lock_acquire_time")
                    .description("Time taken to acquire a lock")
                    .tags(createTags(key, name))
                    .register(meterRegistry)
                    .record(java.time.Duration.ofMillis(acquireTime));

            // 增加活跃锁计数
            String mapKey = createMapKey(key, name);
            AtomicInteger counter = activeLockCountMap.computeIfAbsent(mapKey, k -> new AtomicInteger(0));
            int newCount = counter.incrementAndGet();

            // 更新活跃锁数量gauge
            Gauge.builder("cyx_lock_active_count", () -> counter.get())
                    .description("Number of currently held locks")
                    .tags(createTags(key, name))
                    .register(meterRegistry);

            log.debug("Incremented active lock count for key: {}, name: {}, new count: {}", key, name, newCount);
        } catch (Exception e) {
            log.warn("Failed to record lock acquire success metrics", e);
        }
    }

    @Override
    public void recordLockFailure(String key, String name) {
        try {
            Counter.builder("cyx_lock_acquire_failure_total")
                    .description("Total number of failed lock acquire attempts")
                    .tags(createTags(key, name))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("Failed to record lock acquire failure metrics", e);
        }
    }

    @Override
    public void recordLockReleased(String key, String name, long heldTime) {
        try {
            // 记录持锁时间
            Timer.builder("cyx_lock_held_time")
                    .description("Time a lock was held")
                    .tags(createTags(key, name))
                    .register(meterRegistry)
                    .record(java.time.Duration.ofMillis(heldTime));

            // 减少活跃锁计数
            String mapKey = createMapKey(key, name);
            AtomicInteger counter = activeLockCountMap.get(mapKey);
            if (counter != null) {
                int newCount = counter.decrementAndGet();
                if (newCount <= 0) {
                    activeLockCountMap.remove(mapKey);
                }
                log.debug("Decremented active lock count for key: {}, name: {}, new count: {}", key, name, newCount);
            }
        } catch (Exception e) {
            log.warn("Failed to record lock release metrics", e);
        }
    }

    /**
     * 创建标签
     *
     * @param key  锁的key
     * @param name 锁的名称
     * @return 标签数组
     */
    private Tags createTags(String key, String name) {
        Tags tags = Tags.of("key", truncateTagValue(key));
        if (StringUtils.hasText(name)) {
            tags = tags.and("name", truncateTagValue(name));
        }
        return tags;
    }

    /**
     * 创建Map的key
     *
     * @param key  锁的key
     * @param name 锁的名称
     * @return Map的key
     */
    private String createMapKey(String key, String name) {
        return StringUtils.hasText(name) ? key + ":" + name : key;
    }

    /**
     * 截断标签值，防止过长
     *
     * @param value 标签值
     * @return 截断后的标签值
     */
    private String truncateTagValue(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}