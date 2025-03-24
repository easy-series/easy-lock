package com.easy.lock.aspect;

import com.easy.lock.annotation.Lock;
import com.easy.lock.autoconfigure.EasyLockProperties;
import com.easy.lock.core.LockContext;
import com.easy.lock.core.LockManager;
import com.easy.lock.exception.LockAcquireFailedException;
import com.easy.lock.monitor.LockMetrics;
import com.easy.lock.retry.LockRetryTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁切面
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class LockAspect implements ApplicationContextAware {

    private final LockManager lockManager;
    private final LockMetrics lockMetrics;
    private final EasyLockProperties lockProperties;

    private ApplicationContext applicationContext;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Around("@annotation(com.easy.lock.annotation.Lock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取锁注解
        Lock lockAnnotation = method.getAnnotation(Lock.class);

        // 获取锁配置
        String prefix = lockAnnotation.prefix().isEmpty() ? lockProperties.getPrefix() : lockAnnotation.prefix();

        // 生成锁值（用于标识当前线程）
        String lockValue = UUID.randomUUID().toString();

        // 获取锁类型
        Lock.LockType type = lockAnnotation.type();

        // 获取超时配置
        long waitTime = lockAnnotation.waitTime() < 0 ? lockProperties.getWaitTime() : lockAnnotation.waitTime();
        long leaseTime = lockAnnotation.leaseTime() < 0 ? lockProperties.getLeaseTime() : lockAnnotation.leaseTime();
        TimeUnit timeUnit = lockAnnotation.timeUnit() == TimeUnit.MILLISECONDS ? lockProperties.getTimeUnit()
                : lockAnnotation.timeUnit();

        // 获取处理策略
        Lock.FailStrategy failStrategy = lockAnnotation.failStrategy();

        // 记录锁开始时间
        long startTime = System.currentTimeMillis();

        // 根据锁类型生成锁键
        List<String> lockKeys = generateLockKeys(lockAnnotation, joinPoint, method, prefix);

        // 创建锁上下文
        LockContext lockContext = new LockContext(lockKeys.get(0), lockValue, false, lockProperties.getLockType(),
                lockKeys);

        // 是否启用重试
        boolean retryEnabled = lockAnnotation.retryEnabled();

        boolean locked = false;
        try {
            // 根据是否启用重试采用不同的锁定策略
            if (retryEnabled && failStrategy == Lock.FailStrategy.EXCEPTION) {
                locked = acquireLockWithRetry(lockContext, type, waitTime, leaseTime, timeUnit, lockAnnotation);
            } else {
                locked = acquireLock(lockContext, type, waitTime, leaseTime, timeUnit);
            }

            // 记录锁等待时间
            lockMetrics.recordLockWaitTime(lockContext.getLockKey(), System.currentTimeMillis() - startTime);

            // 如果没有获取到锁，根据策略处理
            if (!locked) {
                lockMetrics.recordLockFail(lockContext.getLockKey());

                switch (failStrategy) {
                    case EXCEPTION:
                        throw new LockAcquireFailedException(lockContext.getLockKey());
                    case RETURN_NULL:
                        return null;
                    case CONTINUE:
                        // 继续执行
                        break;
                    default:
                        throw new IllegalArgumentException("不支持的锁失败策略: " + failStrategy);
                }
            } else {
                lockMetrics.recordLockAcquire(lockContext.getLockKey());
            }

            // 执行目标方法
            long methodStartTime = System.currentTimeMillis();
            Object result = joinPoint.proceed();

            // 记录锁持有时间
            if (locked) {
                lockMetrics.recordLockHoldTime(lockContext.getLockKey(), System.currentTimeMillis() - methodStartTime);
            }

            return result;
        } finally {
            // 释放锁
            releaseLock(lockContext, type);
        }
    }

    /**
     * 使用重试机制获取锁
     */
    private boolean acquireLockWithRetry(LockContext lockContext, Lock.LockType type,
            long waitTime, long leaseTime, TimeUnit timeUnit,
            Lock lockAnnotation) {
        // 创建重试配置
        LockRetryTemplate.RetryConfig retryConfig = buildRetryConfig(lockAnnotation);

        try {
            return LockRetryTemplate.execute(() -> {
                return acquireLock(lockContext, type, waitTime, leaseTime, timeUnit);
            }, retryConfig);
        } catch (LockAcquireFailedException e) {
            log.debug("所有重试尝试均失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 构建重试配置
     */
    private LockRetryTemplate.RetryConfig buildRetryConfig(Lock lockAnnotation) {
        // 获取重试配置
        int maxRetries = lockAnnotation.maxRetries();
        long retryInterval = lockAnnotation.retryInterval();
        Lock.RetryStrategy retryStrategy = lockAnnotation.retryStrategy();

        // 创建退避策略
        LockRetryTemplate.BackoffStrategy backoffStrategy;
        if (retryStrategy == Lock.RetryStrategy.EXPONENTIAL) {
            // 指数退避: 初始时间, 倍数因子, 最大等待时间
            backoffStrategy = new LockRetryTemplate.ExponentialBackoffStrategy(
                    retryInterval, 2.0, 10000L);
        } else {
            // 固定间隔
            backoffStrategy = new LockRetryTemplate.FixedBackoffStrategy(retryInterval);
        }

        // 创建重试配置
        return LockRetryTemplate.RetryConfig.builder()
                .maxRetries(maxRetries)
                .backoffStrategy(backoffStrategy)
                .resultPredicate(result -> Boolean.TRUE.equals(result))
                .build();
    }

    /**
     * 获取锁（不带重试）
     */
    private boolean acquireLock(LockContext lockContext, Lock.LockType type,
            long waitTime, long leaseTime, TimeUnit timeUnit) {
        switch (type) {
            case TRY_LOCK:
                return handleTryLock(lockContext, waitTime, leaseTime, timeUnit);
            case LOCK:
                handleLock(lockContext, leaseTime, timeUnit);
                return true;
            case MULTI_LOCK:
                return handleMultiLock(lockContext, waitTime, leaseTime, timeUnit);
            default:
                throw new IllegalArgumentException("不支持的锁类型: " + type);
        }
    }

    /**
     * 处理尝试获取锁
     */
    private boolean handleTryLock(LockContext lockContext, long waitTime, long leaseTime, TimeUnit timeUnit) {
        try {
            boolean acquired = lockManager.tryLock(lockContext.getLockKey(),
                    lockContext.getLockValue(), waitTime, leaseTime, timeUnit);

            lockContext.setLocked(acquired);
            return acquired;
        } catch (Exception e) {
            log.error("尝试获取锁异常", e);
            return false;
        }
    }

    /**
     * 处理获取锁
     */
    private void handleLock(LockContext lockContext, long leaseTime, TimeUnit timeUnit) {
        lockManager.lock(lockContext.getLockKey(), lockContext.getLockValue(), leaseTime, timeUnit);
        lockContext.setLocked(true);
    }

    /**
     * 处理联锁
     */
    private boolean handleMultiLock(LockContext lockContext, long waitTime, long leaseTime, TimeUnit timeUnit) {
        try {
            boolean acquired = lockManager.tryMultiLock(lockContext.getLockKeys(),
                    lockContext.getLockValue(), waitTime, leaseTime, timeUnit);

            lockContext.setLocked(acquired);
            return acquired;
        } catch (Exception e) {
            log.error("尝试获取联锁异常", e);
            return false;
        }
    }

    /**
     * 释放锁
     */
    private void releaseLock(LockContext lockContext, Lock.LockType type) {
        if (!lockContext.isLocked()) {
            return;
        }

        try {
            if (type == Lock.LockType.MULTI_LOCK) {
                lockManager.releaseMultiLock(lockContext.getLockKeys(), lockContext.getLockValue());
            } else {
                lockManager.releaseLock(lockContext.getLockKey(), lockContext.getLockValue());
            }
        } catch (Exception e) {
            log.error("释放锁异常", e);
        }
    }

    /**
     * 生成锁键
     */
    private List<String> generateLockKeys(Lock lockAnnotation, ProceedingJoinPoint joinPoint, Method method,
            String prefix) {
        List<String> lockKeys = new ArrayList<>();

        // 获取锁类型
        Lock.LockType type = lockAnnotation.type();

        if (type == Lock.LockType.MULTI_LOCK) {
            // 联锁模式，需要多个锁键
            String[] keys = lockAnnotation.keys();
            if (keys.length == 0) {
                throw new IllegalArgumentException("联锁模式下必须指定keys属性");
            }

            for (String key : keys) {
                // 解析 SpEL 表达式
                String lockKey = parseKey(key, method, joinPoint.getArgs());
                lockKeys.add(prefix + ":" + lockKey);
            }
        } else {
            // 单锁模式
            String key = lockAnnotation.key();
            if (key.isEmpty()) {
                // 如果未指定key，使用类名:方法名
                key = method.getDeclaringClass().getSimpleName() + ":" + method.getName();
            } else {
                // 解析 SpEL 表达式
                key = parseKey(key, method, joinPoint.getArgs());
            }
            lockKeys.add(prefix + ":" + key);
        }

        return lockKeys;
    }

    /**
     * 解析 SpEL 表达式
     */
    private String parseKey(String key, Method method, Object[] args) {
        if (!key.contains("#")) {
            return key;
        }

        // 创建评估上下文
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 设置 Bean 解析器
        context.setBeanResolver(new BeanFactoryResolver(applicationContext));

        // 设置方法参数
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        // 设置内置变量
        context.setVariable("method", method);
        context.setVariable("args", args);
        context.setVariable("methodName", method.getName());
        context.setVariable("className", method.getDeclaringClass().getSimpleName());

        // 计算表达式
        return parser.parseExpression(key).getValue(context, String.class);
    }
}