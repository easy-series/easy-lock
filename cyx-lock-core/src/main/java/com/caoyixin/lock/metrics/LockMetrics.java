package com.caoyixin.lock.metrics;

/**
 * 锁监控指标接口，用于记录锁的指标数据
 *
 * @author caoyixin
 */
public interface LockMetrics {

    /**
     * 记录锁获取尝试次数
     *
     * @param key 锁的key
     * @param name 锁的名称
     */
    void recordLockAttempt(String key, String name);

    /**
     * 记录锁获取成功
     *
     * @param key 锁的key
     * @param name 锁的名称
     * @param acquireTime 获取锁耗时(毫秒)
     */
    void recordLockSuccess(String key, String name, long acquireTime);

    /**
     * 记录锁获取失败
     *
     * @param key 锁的key
     * @param name 锁的名称
     */
    void recordLockFailure(String key, String name);

    /**
     * 记录锁释放
     *
     * @param key 锁的key
     * @param name 锁的名称
     * @param heldTime 持锁时间(毫秒)
     */
    void recordLockReleased(String key, String name, long heldTime);
} 