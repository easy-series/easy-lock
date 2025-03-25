package com.caoyixin.lock.support;

import com.caoyixin.lock.exception.LockException;

/**
 * 默认锁失败策略实现，抛出异常
 *
 * @author caoyixin
 */
public class DefaultLockFailureStrategy implements LockFailureStrategy {

    @Override
    public Object onLockFailure(String key, long acquireTimeout) {
        throw new LockException("Failed to acquire lock: " + key + ", acquireTimeout: " + acquireTimeout + "ms");
    }
} 