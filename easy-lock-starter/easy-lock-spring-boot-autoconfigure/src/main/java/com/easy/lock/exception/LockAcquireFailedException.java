package com.easy.lock.exception;

/**
 * 获取锁失败异常
 */
public class LockAcquireFailedException extends LockException {

    public LockAcquireFailedException(String lockKey) {
        super("获取锁失败: " + lockKey);
    }

    public LockAcquireFailedException(String lockKey, Throwable cause) {
        super("获取锁失败: " + lockKey, cause);
    }
}