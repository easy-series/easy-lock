package com.easy.lock.aspect;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.easy.lock.annotation.Lock;
import com.easy.lock.config.LockProperties;
import com.easy.lock.exception.LockAcquireFailedException;
import com.easy.lock.exception.LockException;
import com.easy.lock.monitor.LockMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LockAspect {

    private final RedissonClient redissonClient;
    private final LockProperties lockProperties;
    private final LockMetrics lockMetrics;
    private final SpelExpressionParser spelExpressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.easy.lock.annotation.Lock)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Lock lock = method.getAnnotation(Lock.class);

        try {
            switch (lock.type()) {
                case LOCK:
                    return handleLock(point, lock);
                case MULTI_LOCK:
                    return handleMultiLock(point, lock);
                case TRY_LOCK:
                default:
                    return handleTryLock(point, lock);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockException("获取锁被中断", e);
        }
    }

    private Object handleTryLock(ProceedingJoinPoint point, Lock lock) throws Throwable {
        String lockKey = getLockKey(lock, point);
        RLock rLock = getLock(lockKey, lock.mode());

        long waitTime = getWaitTime(lock);
        long leaseTime = getLeaseTime(lock);
        TimeUnit timeUnit = getTimeUnit(lock);

        boolean locked = false;
        long startTime = System.currentTimeMillis();
        try {
            log.debug("尝试获取{}锁: {}, 等待时间: {}ms, 持有时间: {}ms",
                    lock.mode() == Lock.LockMode.READ ? "读" : "写",
                    lockKey, waitTime, leaseTime);
            locked = rLock.tryLock(waitTime, leaseTime, timeUnit);
            long waitDuration = System.currentTimeMillis() - startTime;
            lockMetrics.recordLockWaitTime(lockKey, waitDuration);

            if (!locked) {
                log.warn("获取{}锁失败: {}", lock.mode() == Lock.LockMode.READ ? "读" : "写", lockKey);
                lockMetrics.recordLockFail(lockKey);
                throw new LockAcquireFailedException(lockKey);
            }

            lockMetrics.recordLockAcquire(lockKey);
            log.debug("获取{}锁成功: {}", lock.mode() == Lock.LockMode.READ ? "读" : "写", lockKey);

            long holdStartTime = System.currentTimeMillis();
            try {
                return point.proceed();
            } finally {
                long holdDuration = System.currentTimeMillis() - holdStartTime;
                lockMetrics.recordLockHoldTime(lockKey, holdDuration);
            }
        } finally {
            if (locked && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                log.debug("释放{}锁: {}", lock.mode() == Lock.LockMode.READ ? "读" : "写", lockKey);
            }
        }
    }

    private Object handleLock(ProceedingJoinPoint point, Lock lock) throws Throwable {
        String lockKey = getLockKey(lock, point);
        RLock rLock = getLock(lockKey, lock.mode());

        long startTime = System.currentTimeMillis();
        rLock.lock();
        long waitDuration = System.currentTimeMillis() - startTime;
        lockMetrics.recordLockWaitTime(lockKey, waitDuration);
        lockMetrics.recordLockAcquire(lockKey);

        try {
            log.debug("获取{}锁成功: {}", lock.mode() == Lock.LockMode.READ ? "读" : "写", lockKey);

            long holdStartTime = System.currentTimeMillis();
            try {
                return point.proceed();
            } finally {
                long holdDuration = System.currentTimeMillis() - holdStartTime;
                lockMetrics.recordLockHoldTime(lockKey, holdDuration);
            }
        } finally {
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                log.debug("释放{}锁: {}", lock.mode() == Lock.LockMode.READ ? "读" : "写", lockKey);
            }
        }
    }

    private Object handleMultiLock(ProceedingJoinPoint point, Lock lock) throws Throwable {
        List<String> lockKeys = getMultiLockKeys(lock, point);
        RLock[] locks = lockKeys.stream()
                .map(key -> getLock(key, lock.mode()))
                .toArray(RLock[]::new);

        RLock multiLock = redissonClient.getMultiLock(locks);
        String lockKeysStr = String.join(",", lockKeys);

        long startTime = System.currentTimeMillis();
        boolean locked = false;

        try {
            if (lock.type() == Lock.LockType.TRY_LOCK) {
                long waitTime = getWaitTime(lock);
                long leaseTime = getLeaseTime(lock);
                TimeUnit timeUnit = getTimeUnit(lock);

                log.debug("尝试获取{}联锁: {}, 等待时间: {}ms, 持有时间: {}ms",
                        lock.mode() == Lock.LockMode.READ ? "读" : "写",
                        lockKeysStr, waitTime, leaseTime);
                locked = multiLock.tryLock(waitTime, leaseTime, timeUnit);

                long waitDuration = System.currentTimeMillis() - startTime;
                lockMetrics.recordLockWaitTime(lockKeysStr, waitDuration);

                if (!locked) {
                    log.warn("获取{}联锁失败: {}", lock.mode() == Lock.LockMode.READ ? "读" : "写", lockKeysStr);
                    lockMetrics.recordLockFail(lockKeysStr);
                    throw new LockAcquireFailedException(lockKeysStr);
                }
            } else {
                multiLock.lock();
                locked = true;
                long waitDuration = System.currentTimeMillis() - startTime;
                lockMetrics.recordLockWaitTime(lockKeysStr, waitDuration);
            }

            lockMetrics.recordLockAcquire(lockKeysStr);
            log.debug("获取{}联锁成功: {}", lock.mode() == Lock.LockMode.READ ? "读" : "写", lockKeysStr);

            long holdStartTime = System.currentTimeMillis();
            try {
                return point.proceed();
            } finally {
                long holdDuration = System.currentTimeMillis() - holdStartTime;
                lockMetrics.recordLockHoldTime(lockKeysStr, holdDuration);
            }
        } finally {
            if (locked && multiLock.isHeldByCurrentThread()) {
                multiLock.unlock();
                log.debug("释放{}联锁: {}", lock.mode() == Lock.LockMode.READ ? "读" : "写", lockKeysStr);
            }
        }
    }

    private RLock getLock(String lockKey, Lock.LockMode mode) {
        if (mode == Lock.LockMode.READ) {
            RReadWriteLock rwLock = redissonClient.getReadWriteLock(lockKey);
            return rwLock.readLock();
        } else {
            if (mode == Lock.LockMode.WRITE) {
                RReadWriteLock rwLock = redissonClient.getReadWriteLock(lockKey);
                return rwLock.writeLock();
            } else {
                return redissonClient.getLock(lockKey);
            }
        }
    }

    private String getLockKey(Lock lock, ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        String key = lock.key();

        String prefix = StringUtils.hasText(lock.prefix()) ? lock.prefix() : lockProperties.getPrefix();

        StringBuilder lockKey = new StringBuilder(prefix);
        if (!StringUtils.hasText(key)) {
            lockKey.append(":").append(method.getDeclaringClass().getSimpleName())
                    .append(":").append(method.getName());
            return lockKey.toString();
        }

        String keyValue = parseSpEL(key, method, point.getArgs());
        if (keyValue != null) {
            lockKey.append(":").append(keyValue);
        }

        return lockKey.toString();
    }

    private List<String> getMultiLockKeys(Lock lock, ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        List<String> keys = new ArrayList<>();

        String prefix = StringUtils.hasText(lock.prefix()) ? lock.prefix() : lockProperties.getPrefix();

        for (String key : lock.keys()) {
            StringBuilder lockKey = new StringBuilder(prefix);
            String keyValue = parseSpEL(key, method, point.getArgs());
            if (keyValue != null) {
                lockKey.append(":").append(keyValue);
                keys.add(lockKey.toString());
            }
        }

        if (keys.isEmpty()) {
            throw new LockException("联锁的key不能为空");
        }

        return keys;
    }

    private String parseSpEL(String spEL, Method method, Object[] args) {
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames == null) {
            return null;
        }

        Expression expression = spelExpressionParser.parseExpression(spEL);
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return expression.getValue(context, String.class);
    }

    private long getWaitTime(Lock lock) {
        return lock.waitTime() > 0 ? lock.waitTime() : lockProperties.getWaitTime();
    }

    private long getLeaseTime(Lock lock) {
        return lock.leaseTime() > 0 ? lock.leaseTime() : lockProperties.getLeaseTime();
    }

    private TimeUnit getTimeUnit(Lock lock) {
        return lock.timeUnit() != null ? lock.timeUnit() : lockProperties.getTimeUnit();
    }
}