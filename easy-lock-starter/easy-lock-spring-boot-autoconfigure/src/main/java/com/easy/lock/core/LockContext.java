package com.easy.lock.core;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 锁上下文，包含锁的相关信息
 */
@Getter
public class LockContext {
    /**
     * 锁的key
     */
    private final String lockKey;

    /**
     * 锁的值，用于标识锁的持有者
     */
    private final String lockValue;

    /**
     * 是否已锁定
     */
    @Setter
    private boolean locked;

    /**
     * 锁类型
     */
    private final LockExecutor.LockType lockType;

    /**
     * 多个锁的key列表（联锁）
     */
    private final List<String> lockKeys;

    /**
     * 重入计数器
     */
    private final AtomicInteger reentrantCount = new AtomicInteger(0);

    /**
     * 锁创建时间
     */
    private final long createTime = System.currentTimeMillis();

    /**
     * 锁定线程的ID
     */
    private final long threadId = Thread.currentThread().getId();

    /**
     * 构造函数
     *
     * @param lockKey   锁的key
     * @param lockValue 锁的值
     * @param locked    是否已锁定
     * @param lockType  锁类型
     */
    public LockContext(String lockKey, String lockValue, boolean locked, LockExecutor.LockType lockType) {
        this(lockKey, lockValue, locked, lockType, null);
    }

    /**
     * 构造函数
     *
     * @param lockKey   锁的key
     * @param lockValue 锁的值
     * @param locked    是否已锁定
     * @param lockType  锁类型
     * @param lockKeys  多个锁的key列表（联锁）
     */
    public LockContext(String lockKey, String lockValue, boolean locked, LockExecutor.LockType lockType,
            List<String> lockKeys) {
        this.lockKey = lockKey;
        this.lockValue = lockValue;
        this.locked = locked;
        this.lockType = lockType;
        this.lockKeys = lockKeys;

        if (locked) {
            // 初始化时如果已锁定，则计数器设为1
            reentrantCount.set(1);
        }
    }

    /**
     * 增加重入计数
     * 
     * @return 增加后的计数值
     */
    public int incrementReentrantCount() {
        return reentrantCount.incrementAndGet();
    }

    /**
     * 减少重入计数
     * 
     * @return 减少后的计数值
     */
    public int decrementReentrantCount() {
        return reentrantCount.decrementAndGet();
    }

    /**
     * 获取当前重入计数
     * 
     * @return 当前重入计数
     */
    public int getReentrantCount() {
        return reentrantCount.get();
    }

    /**
     * 检查是否是当前线程持有的锁
     * 
     * @return 是否是当前线程持有的锁
     */
    public boolean isHeldByCurrentThread() {
        return threadId == Thread.currentThread().getId();
    }

    /**
     * 获取锁已持有的时间（毫秒）
     * 
     * @return 锁已持有的时间
     */
    public long getHoldTime() {
        if (!locked) {
            return 0;
        }
        return System.currentTimeMillis() - createTime;
    }

    @Override
    public String toString() {
        return "LockContext{" +
                "key='" + lockKey + '\'' +
                ", value='" + lockValue + '\'' +
                ", locked=" + locked +
                ", type=" + lockType +
                ", reentrantCount=" + reentrantCount +
                ", threadId=" + threadId +
                '}';
    }
}