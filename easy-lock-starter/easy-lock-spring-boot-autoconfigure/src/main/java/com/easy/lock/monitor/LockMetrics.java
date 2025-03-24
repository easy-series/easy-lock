package com.easy.lock.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

/**
 * 锁监控指标
 */
@Slf4j
public class LockMetrics {

    private MeterRegistry registry;

    @Autowired(required = false)
    public void setMeterRegistry(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录锁获取成功
     *
     * @param lockKey 锁的key
     */
    public void recordLockAcquire(String lockKey) {
        if (registry != null) {
            Counter.builder("lock.acquire.total")
                    .tag("key", lockKey)
                    .description("锁获取总次数")
                    .register(registry)
                    .increment();
        }
    }

    /**
     * 记录锁获取失败
     *
     * @param lockKey 锁的key
     */
    public void recordLockFail(String lockKey) {
        if (registry != null) {
            Counter.builder("lock.acquire.failure")
                    .tag("key", lockKey)
                    .description("锁获取失败次数")
                    .register(registry)
                    .increment();
        }
    }

    /**
     * 记录锁等待时间
     *
     * @param lockKey        锁的key
     * @param waitTimeMillis 等待时间（毫秒）
     */
    public void recordLockWaitTime(String lockKey, long waitTimeMillis) {
        if (registry != null) {
            Timer.builder("lock.wait.time")
                    .tag("key", lockKey)
                    .description("等待获取锁的时间")
                    .register(registry)
                    .record(waitTimeMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 记录锁持有时间
     *
     * @param lockKey        锁的key
     * @param holdTimeMillis 持有时间（毫秒）
     */
    public void recordLockHoldTime(String lockKey, long holdTimeMillis) {
        if (registry != null) {
            Timer.builder("lock.hold.time")
                    .tag("key", lockKey)
                    .description("持有锁的时间")
                    .register(registry)
                    .record(holdTimeMillis, TimeUnit.MILLISECONDS);
        }
    }
}