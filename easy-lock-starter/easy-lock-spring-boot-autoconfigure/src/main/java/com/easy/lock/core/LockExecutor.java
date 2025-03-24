package com.easy.lock.core;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁执行器接口
 * 所有锁实现都需要实现此接口
 */
public interface LockExecutor {

    /**
     * 获取锁类型
     * 
     * @return 锁实现的类型
     */
    LockType getLockType();

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
    boolean tryLock(String lockKey, String lockValue, long waitTime, long leaseTime, TimeUnit timeUnit);

    /**
     * 获取锁（一直尝试，直到获取成功或被中断）
     *
     * @param lockKey   锁的key
     * @param lockValue 锁的值
     * @param leaseTime 持有锁的时间
     * @param timeUnit  时间单位
     */
    void lock(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit);

    /**
     * 释放锁
     *
     * @param lockKey   锁的key
     * @param lockValue 锁的值，用于校验是否是锁的持有者
     * @return 是否释放成功
     */
    boolean releaseLock(String lockKey, String lockValue);

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
    boolean tryMultiLock(List<String> lockKeys, String lockValue, long waitTime, long leaseTime, TimeUnit timeUnit);

    /**
     * 释放多把锁
     *
     * @param lockKeys  锁的key列表
     * @param lockValue 锁的值
     * @return 是否全部释放成功
     */
    boolean releaseMultiLock(List<String> lockKeys, String lockValue);

    /**
     * 锁类型枚举
     */
    enum LockType {
        /**
         * Redis实现
         */
        REDIS,

        /**
         * 数据库实现
         */
        JDBC
    }
}