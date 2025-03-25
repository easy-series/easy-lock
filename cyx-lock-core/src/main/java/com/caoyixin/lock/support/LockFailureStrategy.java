package com.caoyixin.lock.support;

/**
 * 锁失败策略接口，定义获取锁失败后的处理策略
 *
 * @author caoyixin
 */
public interface LockFailureStrategy {

    /**
     * 获取锁失败时的处理方法
     *
     * @param key 锁的key
     * @param acquireTimeout 获取锁的超时时间，单位：毫秒
     * @return 处理结果，可以是异常或者默认值
     */
    Object onLockFailure(String key, long acquireTimeout);
} 