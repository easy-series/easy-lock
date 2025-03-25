package com.caoyixin.lock.core;

import com.caoyixin.lock.exception.LockException;
import com.caoyixin.lock.metrics.LockMetrics;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 锁模板类，封装锁的基本操作
 *
 * @author caoyixin
 */
@Slf4j
public class LockTemplate {

    @Setter
    private LockExecutor lockExecutor;

    @Setter
    private LockMetrics lockMetrics;

    /**
     * 获取锁
     *
     * @param key            锁的key
     * @param expire         锁的过期时间，单位：毫秒
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @return 锁信息，如果获取失败返回null
     */
    public LockInfo lock(String key, long expire, long acquireTimeout) {
        return lock(key, expire, acquireTimeout, null);
    }

    /**
     * 获取锁
     *
     * @param key            锁的key
     * @param expire         锁的过期时间，单位：毫秒
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @param name           锁的名称
     * @return 锁信息，如果获取失败返回null
     */
    public LockInfo lock(String key, long expire, long acquireTimeout, String name) {
        log.debug("Trying to acquire lock, key: {}, expire: {}, acquireTimeout: {}, name: {}",
                key, expire, acquireTimeout, name);

        long startTime = System.currentTimeMillis();

        // 记录获取锁的尝试次数
        if (lockMetrics != null) {
            lockMetrics.recordLockAttempt(key, name);
        }

        // 获取锁
        LockInfo lockInfo = lockExecutor.acquire(key, expire, acquireTimeout);

        // 获取锁成功
        if (lockInfo != null) {
            // 设置锁的基本信息
            String value = UUID.randomUUID().toString();
            lockInfo.setValue(value)
                    .setLockedAt(System.currentTimeMillis())
                    .setExpireTime(System.currentTimeMillis() + expire)
                    .setState(LockInfo.LockState.LOCKED)
                    .setName(name)
                    .setAcquireTime(System.currentTimeMillis() - startTime);

            // 记录获取锁成功的指标
            if (lockMetrics != null) {
                lockMetrics.recordLockSuccess(key, name, lockInfo.getAcquireTime());
            }

            log.debug("Acquired lock successfully, key: {}, value: {}, expire: {}, acquireTime: {}ms",
                    key, value, expire, lockInfo.getAcquireTime());
            return lockInfo;
        }

        // 获取锁失败
        if (lockMetrics != null) {
            lockMetrics.recordLockFailure(key, name);
        }

        log.debug("Failed to acquire lock, key: {}, expire: {}, acquireTimeout: {}",
                key, expire, acquireTimeout);
        return null;
    }

    /**
     * 释放锁
     *
     * @param lockInfo 锁信息
     * @return 是否成功释放
     */
    public boolean releaseLock(LockInfo lockInfo) {
        if (lockInfo == null) {
            return false;
        }

        // 重入次数大于0，则减少重入次数
        if (lockInfo.getReentrantCount() > 0) {
            lockInfo.setReentrantCount(lockInfo.getReentrantCount() - 1);
            return true;
        }

        log.debug("Trying to release lock, key: {}, value: {}",
                lockInfo.getKey(), lockInfo.getValue());

        long heldTime = 0;
        if (lockInfo.getLockedAt() != null) {
            heldTime = System.currentTimeMillis() - lockInfo.getLockedAt();
        }

        // 释放锁
        boolean result = lockExecutor.release(lockInfo);

        // 记录锁的持有时间
        if (result && lockMetrics != null) {
            lockMetrics.recordLockReleased(lockInfo.getKey(), lockInfo.getName(), heldTime);
        }

        // 更新锁状态
        if (result) {
            lockInfo.setState(LockInfo.LockState.UNLOCKED);
            log.debug("Released lock successfully, key: {}, value: {}, heldTime: {}ms",
                    lockInfo.getKey(), lockInfo.getValue(), heldTime);
        } else {
            log.warn("Failed to release lock, key: {}, value: {}",
                    lockInfo.getKey(), lockInfo.getValue());
        }

        return result;
    }

    /**
     * 锁操作模板方法，获取锁后执行操作，操作完成后释放锁
     *
     * @param key            锁的key
     * @param expire         锁的过期时间，单位：毫秒
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @param supplier       获取锁后的操作
     * @param <T>            返回值类型
     * @return 操作结果
     */
    public <T> T executeWithLock(String key, long expire, long acquireTimeout, Supplier<T> supplier) {
        return executeWithLock(key, expire, acquireTimeout, null, supplier);
    }

    /**
     * 锁操作模板方法，获取锁后执行操作，操作完成后释放锁
     *
     * @param key            锁的key
     * @param expire         锁的过期时间，单位：毫秒
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @param name           锁的名称
     * @param supplier       获取锁后的操作
     * @param <T>            返回值类型
     * @return 操作结果
     */
    public <T> T executeWithLock(String key, long expire, long acquireTimeout, String name, Supplier<T> supplier) {
        LockInfo lockInfo = null;
        try {
            // 获取锁
            lockInfo = lock(key, expire, acquireTimeout, name);
            if (lockInfo == null) {
                throw new LockException("Failed to acquire lock, key: " + key);
            }

            // 执行业务操作
            return supplier.get();
        } finally {
            // 释放锁
            if (lockInfo != null) {
                releaseLock(lockInfo);
            }
        }
    }

    /**
     * 锁操作模板方法，获取锁后执行操作，操作完成后释放锁
     *
     * @param key            锁的key
     * @param expire         锁的过期时间，单位：毫秒
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @param runnable       获取锁后的操作
     */
    public void executeWithLock(String key, long expire, long acquireTimeout, Runnable runnable) {
        executeWithLock(key, expire, acquireTimeout, null, runnable);
    }

    /**
     * 锁操作模板方法，获取锁后执行操作，操作完成后释放锁
     *
     * @param key            锁的key
     * @param expire         锁的过期时间，单位：毫秒
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @param name           锁的名称
     * @param runnable       获取锁后的操作
     */
    public void executeWithLock(String key, long expire, long acquireTimeout, String name, Runnable runnable) {
        executeWithLock(key, expire, acquireTimeout, name, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 锁操作模板方法，尝试获取锁后执行操作，如果获取锁失败则返回默认值
     *
     * @param key            锁的key
     * @param expire         锁的过期时间，单位：毫秒
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @param supplier       获取锁后的操作
     * @param defaultValue   获取锁失败时的默认值
     * @param <T>            返回值类型
     * @return 操作结果或默认值
     */
    public <T> T executeWithLockReturnDefault(String key, long expire, long acquireTimeout,
            Supplier<T> supplier, T defaultValue) {
        return executeWithLockReturnDefault(key, expire, acquireTimeout, null, supplier, defaultValue);
    }

    /**
     * 锁操作模板方法，尝试获取锁后执行操作，如果获取锁失败则返回默认值
     *
     * @param key            锁的key
     * @param expire         锁的过期时间，单位：毫秒
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @param name           锁的名称
     * @param supplier       获取锁后的操作
     * @param defaultValue   获取锁失败时的默认值
     * @param <T>            返回值类型
     * @return 操作结果或默认值
     */
    public <T> T executeWithLockReturnDefault(String key, long expire, long acquireTimeout,
            String name, Supplier<T> supplier, T defaultValue) {
        LockInfo lockInfo = null;
        try {
            // 获取锁
            lockInfo = lock(key, expire, acquireTimeout, name);
            if (lockInfo == null) {
                return defaultValue;
            }

            // 执行业务操作
            return supplier.get();
        } finally {
            // 释放锁
            if (lockInfo != null) {
                releaseLock(lockInfo);
            }
        }
    }

    /**
     * 检查锁是否已经被获取
     *
     * @param key 锁的key
     * @return 锁是否已被获取
     */
    public boolean isLocked(String key) {
        return lockExecutor.isLocked(key);
    }

    /**
     * 等待锁释放
     *
     * @param key      锁的key
     * @param waitTime 最长等待时间，单位：毫秒
     * @return 是否等待成功（锁被释放）
     */
    public boolean waitForLock(String key, long waitTime) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < waitTime) {
            if (!isLocked(key)) {
                return true;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}