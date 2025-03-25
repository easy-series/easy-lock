package com.caoyixin.lock.starter.aspect;

import com.caoyixin.lock.annotation.CyxLock;
import com.caoyixin.lock.core.LockInfo;
import com.caoyixin.lock.core.LockTemplate;
import com.caoyixin.lock.support.LockFailureStrategy;
import com.caoyixin.lock.support.LockKeyBuilder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

/**
 * 锁切面类，处理@CyxLock注解
 *
 * @author caoyixin
 */
@Slf4j
@Aspect
@Order(0) // 确保锁在事务之前执行
public class LockAspect {

    @Setter
    private LockTemplate lockTemplate;

    @Setter
    private LockKeyBuilder keyBuilder;

    @Setter
    private LockFailureStrategy failureStrategy;

    /**
     * 环绕通知，处理加锁和解锁
     *
     * @param joinPoint 切点
     * @return 方法执行结果
     * @throws Throwable 可能抛出的异常
     */
    @Around("@annotation(com.caoyixin.lock.annotation.CyxLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解
        CyxLock lockAnnotation = method.getAnnotation(CyxLock.class);
        if (lockAnnotation == null) {
            log.warn("CyxLock annotation not found on method: {}", signature.toShortString());
            return joinPoint.proceed();
        }

        // 构建锁key
        String key = keyBuilder.buildKey(joinPoint, lockAnnotation.keys());

        // 获取锁的其他属性
        long expire = lockAnnotation.expire();
        long acquireTimeout = lockAnnotation.acquireTimeout();
        boolean autoRelease = lockAnnotation.autoRelease();
        String lockName = lockAnnotation.name();

        log.debug(
                "@CyxLock annotation processing, method: {}, key: {}, expire: {}ms, acquireTimeout: {}ms, autoRelease: {}, name: {}",
                signature.toShortString(), key, expire, acquireTimeout, autoRelease, lockName);

        return processWithLock(joinPoint, key, expire, acquireTimeout, autoRelease, lockName);
    }

    /**
     * 通用锁处理逻辑
     */
    private Object processWithLock(ProceedingJoinPoint joinPoint, String key, long expire,
            long acquireTimeout, boolean autoRelease, String lockName) throws Throwable {
        // 获取锁
        LockInfo lockInfo = lockTemplate.lock(key, expire, acquireTimeout, lockName);

        // 如果获取锁失败
        if (lockInfo == null) {
            log.debug("Failed to acquire lock, key: {}, using failure strategy", key);
            return failureStrategy.onLockFailure(key, acquireTimeout);
        }

        try {
            // 执行目标方法
            log.debug("Acquired lock successfully, key: {}, proceed with method execution", key);
            return joinPoint.proceed();
        } finally {
            // 自动释放锁
            if (autoRelease) {
                log.debug("Auto releasing lock, key: {}", key);
                lockTemplate.releaseLock(lockInfo);
            }
        }
    }
}