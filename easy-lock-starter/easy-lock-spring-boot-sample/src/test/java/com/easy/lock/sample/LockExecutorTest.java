package com.easy.lock.sample;

import com.easy.lock.core.LockExecutor;
import com.easy.lock.core.LockManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 锁执行器测试
 */
@SpringBootTest
@Slf4j
@Import({ RedisTestConfiguration.class, TestRedisLockConfiguration.class })
public class LockExecutorTest {

    @Autowired
    private List<LockExecutor> lockExecutors;

    @Autowired
    private LockManager lockManager;

    @Test
    @DisplayName("测试锁执行器是否正常注册")
    public void testLockExecutorsLoaded() {
        log.info("开始测试锁执行器...");

        // 检查是否有执行器
        assertFalse(lockExecutors.isEmpty(), "应该至少有一个锁执行器");
        log.info("发现 {} 个锁执行器", lockExecutors.size());

        // 检查每个执行器的类型
        for (LockExecutor executor : lockExecutors) {
            log.info("锁执行器: {} - 类型: {}", executor.getClass().getSimpleName(), executor.getLockType());
            assertNotNull(executor.getLockType(), "锁类型不应为空");
        }
    }

    @Test
    @DisplayName("测试基本锁操作")
    public void testBasicLockOperations() {
        // 生成一个唯一的测试锁键
        String lockKey = "test:lock:" + UUID.randomUUID().toString();
        String lockValue = UUID.randomUUID().toString();

        log.info("尝试获取锁: key={}, value={}", lockKey, lockValue);

        boolean acquired = lockManager.tryLock(lockKey, lockValue, 1000, 5000, TimeUnit.MILLISECONDS);
        assertTrue(acquired, "应该成功获取锁");

        log.info("成功获取锁");

        try {
            // 尝试重新获取相同的锁（应该失败，除非实现了重入锁）
            String newLockValue = UUID.randomUUID().toString();
            boolean reacquired = lockManager.tryLock(lockKey, newLockValue, 1000, 5000, TimeUnit.MILLISECONDS);
            log.info("尝试重新获取锁: {}", reacquired ? "成功" : "失败");
        } finally {
            // 释放锁
            boolean released = lockManager.releaseLock(lockKey, lockValue);
            assertTrue(released, "应该成功释放锁");
            log.info("成功释放锁");
        }
    }
}