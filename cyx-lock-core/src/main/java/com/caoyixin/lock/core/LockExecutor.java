package com.caoyixin.lock.core;

/**
 * 锁执行器接口，定义获取锁和释放锁的方法
 *
 * @author caoyixin
 */
public interface LockExecutor {

    /**
     * 获取锁
     *
     * @param key            锁的key
     * @param expire         锁的过期时间，单位：毫秒
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @return 锁信息，如果获取失败返回null
     */
    LockInfo acquire(String key, long expire, long acquireTimeout);

    /**
     * 释放锁
     *
     * @param lockInfo 锁信息
     * @return 是否成功释放
     */
    boolean release(LockInfo lockInfo);

    /**
     * 查询锁状态
     *
     * @param key 锁的key
     * @return 锁是否已被获取
     */
    boolean isLocked(String key);

    /**
     * 重入锁
     *
     * @param lockInfo 锁信息
     * @return 更新后的锁信息
     */
    default LockInfo reentry(LockInfo lockInfo) {
        if (lockInfo != null) {
            lockInfo.setReentrantCount(lockInfo.getReentrantCount() + 1);
        }
        return lockInfo;
    }
}