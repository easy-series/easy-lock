package com.easy.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
     * 默认使用配置文件中的prefix值
     */
    String prefix() default "";

    /**
     * 锁的key，支持 SpEL 表达式
     * 如果不指定，将使用类名:方法名作为key
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
     * 锁模式
     */
    LockMode mode() default LockMode.WRITE;

    /**
     * 等待获取锁的时间
     * 默认使用配置文件中的waitTime值
     * 仅在type为TRY_LOCK时生效
     */
    long waitTime() default -1L;

    /**
     * 持有锁的时间
     * 默认使用配置文件中的leaseTime值
     * 仅在type为TRY_LOCK时生效
     */
    long leaseTime() default -1L;

    /**
     * 时间单位
     * 默认使用配置文件中的timeUnit值
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

    /**
     * 锁模式枚举
     */
    enum LockMode {
        /**
         * 写锁（排他锁）
         */
        WRITE,

        /**
         * 读锁（共享锁）
         */
        READ
    }
}