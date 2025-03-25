package com.caoyixin.lock.redisson.executor;

import com.caoyixin.lock.core.LockExecutor;
import com.caoyixin.lock.core.LockInfo;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 基于Redisson的锁执行器实现
 *
 * @author caoyixin
 */
@Slf4j
public class RedissonLockExecutor implements LockExecutor {

    @Setter
    private RedissonClient redissonClient;

    @Override
    public LockInfo acquire(String key, long expire, long acquireTimeout) {
        // 参数校验
        if (redissonClient == null) {
            throw new IllegalStateException("RedissonClient is not initialized");
        }

        log.debug("Trying to acquire lock with Redisson, key: {}, expire: {}ms, acquireTimeout: {}ms",
                key, expire, acquireTimeout);

        // 获取锁对象
        RLock lock = redissonClient.getLock(key);

        try {
            // 尝试获取锁
            boolean success = lock.tryLock(acquireTimeout, expire, TimeUnit.MILLISECONDS);

            if (success) {
                log.debug("Acquired lock with Redisson successfully, key: {}", key);

                // 创建锁信息对象
                LockInfo lockInfo = new LockInfo();
                lockInfo.setKey(key);

                return lockInfo;
            } else {
                log.debug("Failed to acquire lock with Redisson, key: {}", key);
                return null;
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while trying to acquire lock with Redisson, key: {}", key, e);
            // 恢复中断状态
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("Error occurred while trying to acquire lock with Redisson, key: {}", key, e);
            return null;
        }
    }

    @Override
    public boolean release(LockInfo lockInfo) {
        // 参数校验
        if (redissonClient == null) {
            throw new IllegalStateException("RedissonClient is not initialized");
        }
        if (lockInfo == null || lockInfo.getKey() == null) {
            return false;
        }

        String key = lockInfo.getKey();
        log.debug("Trying to release lock with Redisson, key: {}", key);

        try {
            // 获取锁对象
            RLock lock = redissonClient.getLock(key);

            // 检查锁是否被当前线程持有
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                // 释放锁
                lock.unlock();
                log.debug("Released lock with Redisson successfully, key: {}", key);
                return true;
            } else {
                log.warn("Cannot release lock with Redisson, key: {}, not locked by current thread", key);
                return false;
            }
        } catch (Exception e) {
            log.error("Error occurred while trying to release lock with Redisson, key: {}", key, e);
            return false;
        }
    }

    @Override
    public boolean isLocked(String key) {
        // 参数校验
        if (redissonClient == null) {
            throw new IllegalStateException("RedissonClient is not initialized");
        }
        if (key == null) {
            return false;
        }

        try {
            // 获取锁对象
            RLock lock = redissonClient.getLock(key);

            // 检查锁是否被获取
            return lock.isLocked();
        } catch (Exception e) {
            log.error("Error occurred while checking lock status with Redisson, key: {}", key, e);
            return false;
        }
    }
}