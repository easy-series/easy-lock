package com.caoyixin.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁注解，用于标记需要加锁的方法
 *
 * @author caoyixin
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CyxLock {

    /**
     * 锁的key表达式数组，支持SpEL表达式
     * 如 {"#user.id", "#order.id"}
     */
    String[] keys() default {};

    /**
     * 锁的过期时间，单位：毫秒
     * 默认30秒
     */
    long expire() default 30000;

    /**
     * 获取锁的超时时间，单位：毫秒
     * 默认3秒
     */
    long acquireTimeout() default 3000;

    /**
     * 是否自动释放锁
     */
    boolean autoRelease() default true;

    /**
     * 锁的名称，用于日志和监控
     */
    String name() default "";
}