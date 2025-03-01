package com.easy.lock.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {
    /**
     * 锁的key前缀
     */
    String prefix() default "lock";

    /**
     * 锁的key，支持 SpEL 表达式
     */
    String key() default "";

    /**
     * 多个锁的key，支持 SpEL 表达式，用于联锁
     */
    String[] keys() default {};

    /**
     * 锁类型
     */
    LockType type() default LockType.TRY_LOCK;

    /**
     * 等待获取锁的时间，默认3秒
     * 仅在type为TRY_LOCK时生效
     */
    long waitTime() default 3000L;

    /**
     * 持有锁的时间，默认30秒
     * 仅在type为TRY_LOCK时生效
     */
    long leaseTime() default 30000L;

    /**
     * 时间单位，默认毫秒
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 锁类型枚举
     */
    enum LockType {
        /**
         * 尝试获取锁（带超时）
         */
        TRY_LOCK,
        
        /**
         * 永久锁（自动续期）
         */
        LOCK,
        
        /**
         * 联锁（多个key）
         */
        MULTI_LOCK
    }
} 