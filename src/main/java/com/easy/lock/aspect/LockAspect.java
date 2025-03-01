package com.easy.lock.aspect;

import com.easy.lock.annotation.Lock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LockAspect {

    private final RedissonClient redissonClient;
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
            throw new RuntimeException("获取锁被中断", e);
        }
    }

    private Object handleTryLock(ProceedingJoinPoint point, Lock lock) throws Throwable {
        String lockKey = getLockKey(lock, point);
        RLock rLock = redissonClient.getLock(lockKey);
        
        boolean locked = false;
        try {
            locked = rLock.tryLock(lock.waitTime(), lock.leaseTime(), lock.timeUnit());
            if (!locked) {
                log.warn("获取锁失败: {}", lockKey);
                return null;
            }
            return point.proceed();
        } finally {
            if (locked) {
                rLock.unlock();
            }
        }
    }

    private Object handleLock(ProceedingJoinPoint point, Lock lock) throws Throwable {
        String lockKey = getLockKey(lock, point);
        RLock rLock = redissonClient.getLock(lockKey);
        
        rLock.lock();
        try {
            return point.proceed();
        } finally {
            rLock.unlock();
        }
    }

    private Object handleMultiLock(ProceedingJoinPoint point, Lock lock) throws Throwable {
        List<String> lockKeys = getMultiLockKeys(lock, point);
        RLock[] locks = lockKeys.stream()
                .map(redissonClient::getLock)
                .toArray(RLock[]::new);
        
        RLock multiLock = redissonClient.getMultiLock(locks);
        multiLock.lock();
        try {
            return point.proceed();
        } finally {
            multiLock.unlock();
        }
    }

    private String getLockKey(Lock lock, ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        String key = lock.key();
        
        StringBuilder lockKey = new StringBuilder(lock.prefix());
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
        
        for (String key : lock.keys()) {
            StringBuilder lockKey = new StringBuilder(lock.prefix());
            String keyValue = parseSpEL(key, method, point.getArgs());
            if (keyValue != null) {
                lockKey.append(":").append(keyValue);
                keys.add(lockKey.toString());
            }
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
} 