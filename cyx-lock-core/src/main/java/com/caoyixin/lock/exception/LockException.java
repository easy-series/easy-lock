package com.caoyixin.lock.exception;

/**
 * 锁异常类
 *
 * @author caoyixin
 */
public class LockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LockException() {
        super();
    }

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