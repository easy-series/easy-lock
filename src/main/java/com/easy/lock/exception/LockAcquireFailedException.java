package com.easy.lock.exception;

/**
 * 获取锁失败异常
 */
public class LockAcquireFailedException extends LockException {

    private final String lockKey;

    public LockAcquireFailedException(String lockKey) {
        super("获取锁失败: " + lockKey);
        this.lockKey = lockKey;
    }

    public LockAcquireFailedException(String lockKey, Throwable cause) {
        super("获取锁失败: " + lockKey, cause);
        this.lockKey = lockKey;
    }

    public String getLockKey() {
        return lockKey;
    }
}