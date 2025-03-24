package com.easy.lock.core;

import com.easy.lock.autoconfigure.EasyLockProperties;
import com.easy.lock.exception.LockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 锁管理器，用于管理不同的锁实现
 */
@Slf4j
public class LockManager {

    private final Map<LockExecutor.LockType, LockExecutor> executors = new ConcurrentHashMap<>();
    private final LockExecutor.LockType defaultLockType;

    /**
     * 线程ID与锁上下文的映射，用于实现重入锁
     */
    private final ConcurrentHashMap<Thread, ConcurrentHashMap<String, LockContext>> threadLockMap = new ConcurrentHashMap<>();

    public LockManager(List<LockExecutor> lockExecutors, LockExecutor.LockType defaultLockType) {
        if (CollectionUtils.isEmpty(lockExecutors)) {
            log.warn("没有找到锁执行器实现，将使用默认的JDBC执行器");
            // 这里不抛出异常，由外部处理
        } else {
            for (LockExecutor executor : lockExecutors) {
                executors.put(executor.getLockType(), executor);
            }
        }

        this.defaultLockType = defaultLockType;
        log.info("锁管理器初始化完成，可用的执行器: {}", executors.keySet());
    }

    /**
     * 尝试获取锁
     *
     * @param lockKey   锁的key
     * @param lockValue 锁的值（通常用于标识锁的持有者）
     * @param waitTime  等待获取锁的时间
     * @param leaseTime 持有锁的时间
     * @param timeUnit  时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, String lockValue, long waitTime, long leaseTime, TimeUnit timeUnit) {
        Thread currentThread = Thread.currentThread();

        // 检查当前线程是否已经持有该锁（重入检查）
        LockContext existingContext = getExistingLockContext(currentThread, lockKey);
        if (existingContext != null && existingContext.isLocked() && existingContext.isHeldByCurrentThread()) {
            // 增加重入计数
            existingContext.incrementReentrantCount();
            log.debug("锁重入: key={}, value={}, count={}", lockKey, lockValue, existingContext.getReentrantCount());
            return true;
        }

        LockExecutor executor = getExecutor(defaultLockType);
        boolean locked = executor.tryLock(lockKey, lockValue, waitTime, leaseTime, timeUnit);

        if (locked) {
            // 创建锁上下文并记录
            LockContext context = new LockContext(lockKey, lockValue, true, defaultLockType);
            recordLock(currentThread, context);
            log.debug("获取锁成功: key={}, value={}", lockKey, lockValue);
        }

        return locked;
    }

    /**
     * 获取锁（一直尝试，直到获取成功或被中断）
     *
     * @param lockKey   锁的key
     * @param lockValue 锁的值
     * @param leaseTime 持有锁的时间
     * @param timeUnit  时间单位
     */
    public void lock(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit) {
        Thread currentThread = Thread.currentThread();

        // 检查当前线程是否已经持有该锁（重入检查）
        LockContext existingContext = getExistingLockContext(currentThread, lockKey);
        if (existingContext != null && existingContext.isLocked() && existingContext.isHeldByCurrentThread()) {
            // 增加重入计数
            existingContext.incrementReentrantCount();
            log.debug("锁重入: key={}, value={}, count={}", lockKey, lockValue, existingContext.getReentrantCount());
            return;
        }

        LockExecutor executor = getExecutor(defaultLockType);
        executor.lock(lockKey, lockValue, leaseTime, timeUnit);

        // 创建锁上下文并记录
        LockContext context = new LockContext(lockKey, lockValue, true, defaultLockType);
        recordLock(currentThread, context);
        log.debug("获取锁成功: key={}, value={}", lockKey, lockValue);
    }

    /**
     * 释放锁
     *
     * @param lockKey   锁的key
     * @param lockValue 锁的值，用于校验是否是锁的持有者
     * @return 是否释放成功
     */
    public boolean releaseLock(String lockKey, String lockValue) {
        Thread currentThread = Thread.currentThread();

        // 获取锁上下文
        LockContext context = getExistingLockContext(currentThread, lockKey);
        if (context == null) {
            log.debug("释放锁失败，未找到锁上下文: key={}, value={}", lockKey, lockValue);
            return false;
        }

        // 检查是否是当前线程持有的锁
        if (!context.isHeldByCurrentThread()) {
            log.warn("释放锁失败，不是当前线程持有的锁: key={}, value={}, holderId={}, currentId={}",
                    lockKey, lockValue, context.getThreadId(), Thread.currentThread().getId());
            return false;
        }

        // 减少重入计数
        int count = context.decrementReentrantCount();
        if (count > 0) {
            // 如果计数器大于0，说明还有重入，不实际释放锁
            log.debug("锁重入计数减少: key={}, value={}, count={}", lockKey, lockValue, count);
            return true;
        }

        // 计数器为0，实际释放锁
        LockExecutor executor = getExecutor(context.getLockType());
        boolean released = executor.releaseLock(lockKey, lockValue);

        if (released) {
            // 移除锁记录
            removeLock(currentThread, lockKey);
            log.debug("释放锁成功: key={}, value={}", lockKey, lockValue);
        } else {
            log.debug("释放锁失败: key={}, value={}", lockKey, lockValue);
        }

        return released;
    }

    /**
     * 获取多把锁（联锁）
     *
     * @param lockKeys  锁的key列表
     * @param lockValue 锁的值
     * @param waitTime  等待获取锁的时间
     * @param leaseTime 持有锁的时间
     * @param timeUnit  时间单位
     * @return 是否全部获取成功
     */
    public boolean tryMultiLock(List<String> lockKeys, String lockValue, long waitTime, long leaseTime,
            TimeUnit timeUnit) {
        Thread currentThread = Thread.currentThread();

        // 检查是否所有锁都已被当前线程持有（重入检查）
        boolean allHeld = true;
        for (String lockKey : lockKeys) {
            LockContext existingContext = getExistingLockContext(currentThread, lockKey);
            if (existingContext == null || !existingContext.isLocked() || !existingContext.isHeldByCurrentThread()) {
                allHeld = false;
                break;
            }
        }

        if (allHeld) {
            // 所有锁都已被当前线程持有，增加重入计数
            for (String lockKey : lockKeys) {
                LockContext existingContext = getExistingLockContext(currentThread, lockKey);
                existingContext.incrementReentrantCount();
            }
            log.debug("联锁重入: keys={}, value={}", lockKeys, lockValue);
            return true;
        }

        LockExecutor executor = getExecutor(defaultLockType);
        boolean locked = executor.tryMultiLock(lockKeys, lockValue, waitTime, leaseTime, timeUnit);

        if (locked) {
            // 创建锁上下文并记录
            for (String lockKey : lockKeys) {
                LockContext context = new LockContext(lockKey, lockValue, true, defaultLockType, lockKeys);
                recordLock(currentThread, context);
            }
            log.debug("获取联锁成功: keys={}, value={}", lockKeys, lockValue);
        }

        return locked;
    }

    /**
     * 释放多把锁
     *
     * @param lockKeys  锁的key列表
     * @param lockValue 锁的值
     * @return 是否全部释放成功
     */
    public boolean releaseMultiLock(List<String> lockKeys, String lockValue) {
        Thread currentThread = Thread.currentThread();

        // 检查是否所有锁都是当前线程持有的锁
        for (String lockKey : lockKeys) {
            LockContext context = getExistingLockContext(currentThread, lockKey);
            if (context == null || !context.isHeldByCurrentThread()) {
                log.warn("释放联锁失败，不是当前线程持有所有锁: keys={}, value={}", lockKeys, lockValue);
                return false;
            }
        }

        // 减少所有锁的重入计数
        boolean shouldReleasePhysical = false;
        for (String lockKey : lockKeys) {
            LockContext context = getExistingLockContext(currentThread, lockKey);
            int count = context.decrementReentrantCount();
            if (count == 0) {
                // 如果任一锁的计数器为0，需要物理释放所有锁
                shouldReleasePhysical = true;
            }
        }

        if (!shouldReleasePhysical) {
            // 如果所有锁的计数器都大于0，不实际释放锁
            log.debug("联锁重入计数减少: keys={}, value={}", lockKeys, lockValue);
            return true;
        }

        // 实际释放所有锁
        LockExecutor executor = getExecutor(defaultLockType);
        boolean released = executor.releaseMultiLock(lockKeys, lockValue);

        if (released) {
            // 移除所有锁记录
            for (String lockKey : lockKeys) {
                removeLock(currentThread, lockKey);
            }
            log.debug("释放联锁成功: keys={}, value={}", lockKeys, lockValue);
        } else {
            log.debug("释放联锁失败: keys={}, value={}", lockKeys, lockValue);
        }

        return released;
    }

    /**
     * 获取对应类型的锁执行器
     *
     * @param lockType 锁类型
     * @return 锁执行器
     */
    private LockExecutor getExecutor(LockExecutor.LockType lockType) {
        // 如果指定了类型，则使用指定类型的执行器
        if (lockType != null) {
            LockExecutor executor = executors.get(lockType);
            if (executor != null) {
                return executor;
            }
            log.warn("未找到类型为 {} 的锁执行器，可用的执行器: {}", lockType, executors.keySet());
        }

        // 如果没有指定类型或指定类型的执行器不存在，则使用默认类型
        if (defaultLockType != null) {
            LockExecutor executor = executors.get(defaultLockType);
            if (executor != null) {
                return executor;
            }
            log.warn("未找到默认类型 {} 的锁执行器，可用的执行器: {}", defaultLockType, executors.keySet());
        }

        // 如果仍未找到执行器，但有其他执行器可用，则使用第一个
        if (!executors.isEmpty()) {
            LockExecutor.LockType firstType = executors.keySet().iterator().next();
            log.warn("未找到指定或默认类型的锁执行器，使用类型 {} 的执行器", firstType);
            return executors.get(firstType);
        }

        // 没有任何可用的执行器
        log.error("未找到任何可用的锁执行器");
        throw new LockException("未找到任何可用的锁执行器，请确保有至少一个锁执行器被正确配置");
    }

    /**
     * 记录线程持有的锁
     *
     * @param thread  线程
     * @param context 锁上下文
     */
    private void recordLock(Thread thread, LockContext context) {
        ConcurrentHashMap<String, LockContext> lockMap = threadLockMap.computeIfAbsent(
                thread, k -> new ConcurrentHashMap<>());
        lockMap.put(context.getLockKey(), context);
    }

    /**
     * 移除线程持有的锁记录
     *
     * @param thread  线程
     * @param lockKey 锁的key
     */
    private void removeLock(Thread thread, String lockKey) {
        ConcurrentHashMap<String, LockContext> lockMap = threadLockMap.get(thread);
        if (lockMap != null) {
            lockMap.remove(lockKey);
            if (lockMap.isEmpty()) {
                threadLockMap.remove(thread);
            }
        }
    }

    /**
     * 获取线程持有的锁上下文
     *
     * @param thread  线程
     * @param lockKey 锁的key
     * @return 锁上下文，如果不存在则返回null
     */
    private LockContext getExistingLockContext(Thread thread, String lockKey) {
        ConcurrentHashMap<String, LockContext> lockMap = threadLockMap.get(thread);
        if (lockMap != null) {
            return lockMap.get(lockKey);
        }
        return null;
    }

    /**
     * 生成锁的值，用于标识锁的持有者
     *
     * @return 唯一的锁值
     */
    private String generateLockValue() {
        return UUID.randomUUID().toString();
    }
}