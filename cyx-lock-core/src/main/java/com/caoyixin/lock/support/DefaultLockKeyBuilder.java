package com.caoyixin.lock.support;

import com.caoyixin.lock.util.SpelUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认锁键生成器实现
 *
 * @author caoyixin
 */
@Slf4j
public class DefaultLockKeyBuilder implements LockKeyBuilder {

    /**
     * 锁键前缀
     */
    @Setter
    private String lockKeyPrefix = "cyx:lock";

    @Override
    public String buildKey(ProceedingJoinPoint joinPoint, String[] keys) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // 获取方法名和类名
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // 构建基础键
        StringBuilder keyBuilder = new StringBuilder(lockKeyPrefix)
                .append(":")
                .append(className)
                .append(":")
                .append(methodName);

        // 如果没有指定键表达式，直接使用方法签名作为键
        if (keys == null || keys.length == 0) {
            return keyBuilder.toString();
        }

        // 解析SpEL表达式
        List<String> keyList = new ArrayList<>(keys.length);
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }

            Object value;
            try {
                value = SpelUtils.parseSpel(joinPoint, key);
            } catch (Exception e) {
                log.warn("Failed to parse SpEL expression: {}, use raw expression", key, e);
                value = key;
            }

            keyList.add(String.valueOf(value));
        }

        // 合并键
        if (!keyList.isEmpty()) {
            keyBuilder.append(":").append(String.join("_", keyList));
        }

        return keyBuilder.toString();
    }
}