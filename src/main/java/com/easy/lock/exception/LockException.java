package com.easy.lock.exception;

/**
 * 分布式锁异常基类
 */
public class LockException extends RuntimeException {
    
    public LockException(String message) {
        super(message);
    }
    
    public LockException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public LockException(Throwable cause) {
        super(cause);
    }
} 