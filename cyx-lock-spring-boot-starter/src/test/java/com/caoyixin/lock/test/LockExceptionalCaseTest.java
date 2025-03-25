package com.caoyixin.lock.test;

import com.caoyixin.lock.core.LockExecutor;
import com.caoyixin.lock.core.LockInfo;
import com.caoyixin.lock.core.LockTemplate;
import com.caoyixin.lock.redisson.executor.RedissonLockExecutor;
import com.caoyixin.lock.test.config.TestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试锁框架的异常情况和边界条件
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestConfiguration.class)
public class LockExceptionalCaseTest {
    private static final Logger logger = LoggerFactory.getLogger(LockExceptionalCaseTest.class);

    @Autowired
    private LockTemplate lockTemplate;

    @Autowired
    private LockExecutor lockExecutor;

    @BeforeEach
    public void setUp() {
        // 确保使用的是Redisson执行器
        assertNotNull(lockExecutor);
        assertTrue(lockExecutor instanceof RedissonLockExecutor);
    }

    /**
     * 测试获取锁失败的情况
     */
    @Test
    public void testLockAcquisitionFailure() throws InterruptedException, ExecutionException {
        String lockKey = "test:failure:lock";
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);

        // 使用CompletableFuture在另一个线程中获取并持有锁
        CompletableFuture<LockInfo> lockFuture = CompletableFuture.supplyAsync(() -> {
            LockInfo lock = lockTemplate.lock(lockKey, 5000, 1000);
            lockAcquiredLatch.countDown(); // 通知主线程锁已获取
            try {
                // 持有锁一段时间
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (lock != null) {
                    lockTemplate.releaseLock(lock);
                }
            }
            return lock;
        });

        // 等待锁被另一个线程获取
        assertTrue(lockAcquiredLatch.await(3, TimeUnit.SECONDS), "锁应该被另一个线程成功获取");

        // 尝试获取同一个锁，应该失败（因为超时时间短）
        LockInfo secondLock = lockTemplate.lock(lockKey, 5000, 500);
        assertNull(secondLock, "应该获取锁失败，因为锁被另一个线程持有且超时时间短");

        // 等待第一个线程完成并释放锁
        lockFuture.get();

        // 确认锁已被释放
        assertFalse(lockExecutor.isLocked(lockKey), "锁应该已被释放");

        // 现在应该能获取锁了
        LockInfo newLock = lockTemplate.lock(lockKey, 5000, 1000);
        assertNotNull(newLock, "锁释放后应该能成功获取");
        lockTemplate.releaseLock(newLock);
    }

    /**
     * 测试释放锁失败的情况 - 释放非当前线程持有的锁
     */
    @Test
    public void testLockReleaseFailure() throws InterruptedException, ExecutionException {
        String lockKey = "test:release:failure";

        // 在当前线程获取锁
        LockInfo lockInfo = lockTemplate.lock(lockKey, 5000, 1000);
        assertNotNull(lockInfo, "应该成功获取锁");

        // 在另一个线程尝试释放这个锁
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            // 创建一个新的锁信息对象，模拟错误的锁信息
            LockInfo wrongLockInfo = new LockInfo().setKey(lockKey);
            return lockTemplate.releaseLock(wrongLockInfo);
        });

        boolean released = future.get();
        assertFalse(released, "另一个线程释放锁应该失败");

        // 正确释放锁
        boolean correctRelease = lockTemplate.releaseLock(lockInfo);
        assertTrue(correctRelease, "正确释放锁应该成功");
    }

    /**
     * 测试释放已释放的锁
     */
    @Test
    public void testReleaseAlreadyReleasedLock() {
        String lockKey = "test:double:release";

        // 获取锁
        LockInfo lockInfo = lockTemplate.lock(lockKey, 5000, 1000);
        assertNotNull(lockInfo, "应该成功获取锁");

        // 第一次释放锁
        boolean firstRelease = lockTemplate.releaseLock(lockInfo);
        assertTrue(firstRelease, "第一次释放锁应该成功");

        // 第二次释放同一个锁
        boolean secondRelease = lockTemplate.releaseLock(lockInfo);
        assertFalse(secondRelease, "第二次释放同一个锁应该失败");
    }

    /**
     * 测试使用空锁信息释放锁
     */
    @Test
    public void testReleaseNullLock() {
        // 使用null尝试释放锁
        boolean released = lockTemplate.releaseLock(null);
        assertFalse(released, "使用null释放锁应该返回false");
    }

    /**
     * 测试锁在Lambda执行过程中发生异常的情况
     */
    @Test
    public void testExceptionInLambda() throws InterruptedException {
        String lockKey = "test:exception:lambda";
        final String exceptionMessage = "测试异常";

        // 检查锁是否被持有
        assertFalse(lockExecutor.isLocked(lockKey), "锁应该未被持有");

        // 获取锁并执行可能抛出异常的Lambda
        try {
            lockTemplate.executeWithLock(lockKey, 5000, 1000, () -> {
                // 确认锁已被获取
                assertTrue(lockExecutor.isLocked(lockKey), "执行Lambda时锁应该被持有");
                throw new RuntimeException(exceptionMessage);
            });
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            // 确认异常信息
            assertEquals(exceptionMessage, e.getMessage(), "应该抛出预期的异常");
        }

        // 验证即使发生异常，锁也应该被释放
        Thread.sleep(100); // 给锁释放一点时间
        assertFalse(lockExecutor.isLocked(lockKey), "即使发生异常也应该释放锁");
    }

    /**
     * 测试零等待时间的锁获取
     */
    @Test
    public void testZeroWaitTimeLock() throws InterruptedException, ExecutionException {
        String lockKey = "test:zerowait:lock";
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);

        // 在另一个线程中获取锁
        CompletableFuture<LockInfo> future = CompletableFuture.supplyAsync(() -> {
            LockInfo lock = lockTemplate.lock(lockKey, 5000, 1000);
            lockAcquiredLatch.countDown(); // 通知主线程锁已获取
            try {
                // 持有锁一段时间
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (lock != null) {
                    lockTemplate.releaseLock(lock);
                }
            }
            return lock;
        });

        // 等待锁被另一个线程获取
        assertTrue(lockAcquiredLatch.await(3, TimeUnit.SECONDS), "锁应该被另一个线程成功获取");

        // 确认锁已被持有
        assertTrue(lockExecutor.isLocked(lockKey), "锁应该已被持有");

        // 尝试以零等待时间获取同一个锁
        LockInfo secondLock = lockTemplate.lock(lockKey, 5000, 0);
        assertNull(secondLock, "零等待时间应该立即返回null而不是阻塞");

        // 等待第一个线程完成并释放锁
        future.get();

        // 确认锁已被释放
        assertFalse(lockExecutor.isLocked(lockKey), "锁应该已被释放");

        // 现在应该能获取锁了
        LockInfo newLock = lockTemplate.lock(lockKey, 5000, 0);
        assertNotNull(newLock, "锁释放后零等待时间也应该能获取成功");
        lockTemplate.releaseLock(newLock);
    }

    /**
     * 测试不同键的锁互不影响
     */
    @Test
    public void testDifferentKeyLocks() {
        String firstKey = "test:first:lock";
        String secondKey = "test:second:lock";

        // 获取第一把锁
        LockInfo firstLock = lockTemplate.lock(firstKey, 5000, 1000);
        assertNotNull(firstLock, "应该成功获取第一把锁");
        assertTrue(lockExecutor.isLocked(firstKey), "第一把锁应该被持有");

        // 获取第二把锁（不同的键）
        LockInfo secondLock = lockTemplate.lock(secondKey, 5000, 1000);
        assertNotNull(secondLock, "应该成功获取第二把锁（不同键）");
        assertTrue(lockExecutor.isLocked(secondKey), "第二把锁应该被持有");

        // 释放锁
        assertTrue(lockTemplate.releaseLock(firstLock), "应该成功释放第一把锁");
        assertFalse(lockExecutor.isLocked(firstKey), "第一把锁应该被释放");

        assertTrue(lockTemplate.releaseLock(secondLock), "应该成功释放第二把锁");
        assertFalse(lockExecutor.isLocked(secondKey), "第二把锁应该被释放");
    }

    /**
     * 测试锁自动释放（过期）
     */
    @Test
    public void testLockExpiration() throws InterruptedException {
        String lockKey = "test:expiration:lock";
        long shortExpire = 1000; // 非常短的过期时间

        // 获取一个短期锁
        LockInfo lock = lockTemplate.lock(lockKey, shortExpire, 1000);
        assertNotNull(lock, "应该成功获取锁");
        assertTrue(lockExecutor.isLocked(lockKey), "锁应该被持有");

        // 等待锁过期
        Thread.sleep(shortExpire + 500);

        // 验证锁已过期
        assertFalse(lockExecutor.isLocked(lockKey), "锁应该已经过期");

        // 尝试获取同一个锁，应该成功
        LockInfo newLock = lockTemplate.lock(lockKey, 5000, 1000);
        assertNotNull(newLock, "第一个锁过期后应该能获取新锁");

        // 释放新锁
        assertTrue(lockTemplate.releaseLock(newLock), "应该成功释放新锁");
    }

    /**
     * 测试锁的非法参数
     */
    @Test
    public void testInvalidParameters() {
        String lockKey = "test:invalid:params";

        // 测试负过期时间 - 在Redisson中，这应该会引发IllegalArgumentException异常
        try {
            lockTemplate.lock(lockKey + ":1", -100, 1000);
            fail("负过期时间应该抛出异常");
        } catch (Exception e) {
            // IllegalArgumentException或其他运行时异常，取决于实现
            assertTrue(e instanceof RuntimeException, "应该抛出运行时异常");
        }

        // 测试负等待时间 - 在Redisson中，这应该会引发IllegalArgumentException异常
        try {
            lockTemplate.lock(lockKey + ":2", 5000, -100);
            fail("负等待时间应该抛出异常");
        } catch (Exception e) {
            // IllegalArgumentException或其他运行时异常，取决于实现
            assertTrue(e instanceof RuntimeException, "应该抛出运行时异常");
        }

        // 测试空键名 - 这取决于底层实现，某些实现可能允许，某些可能抛出异常
        // 我们期望它要么返回null要么抛出异常
        try {
            LockInfo lock3 = lockTemplate.lock("", 5000, 1000);
            if (lock3 != null) {
                // 如果成功获取，确保释放
                lockTemplate.releaseLock(lock3);
            }
        } catch (Exception e) {
            // 如果抛出异常，这是可接受的
            logger.info("空键名测试抛出异常: {}", e.getMessage());
        }

        // 测试null键名 - 应该抛出异常
        try {
            lockTemplate.lock(null, 5000, 1000);
            fail("null键名应该抛出异常");
        } catch (Exception e) {
            // NullPointerException或其他运行时异常，取决于实现
            assertTrue(e instanceof RuntimeException, "应该抛出运行时异常");
        }
    }
}