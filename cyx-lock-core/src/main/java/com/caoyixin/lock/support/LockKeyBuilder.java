package com.caoyixin.lock.support;

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 锁键生成器接口，用于生成锁的key
 *
 * @author caoyixin
 */
public interface LockKeyBuilder {

    /**
     * 构建锁的key
     *
     * @param joinPoint 切点信息
     * @param keys      key表达式数组
     * @return 锁的key
     */
    String buildKey(ProceedingJoinPoint joinPoint, String[] keys);
}