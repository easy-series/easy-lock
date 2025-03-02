package com.easy.lock.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.easy.lock.AbstractIntegrationTest;
import com.easy.lock.exception.LockException;

/**
 * RedisLock API测试
 */
class RedisLockTest extends AbstractIntegrationTest {

    @Autowired
    private RedisLock redisLock;

    private final String testKey = "test:lock:key";
    private final List<String> testMultiKeys = Arrays.asList("test:lock:key1", "test:lock:key2");
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(5);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
        try {
            redisLock.unlock(testKey);
        } catch (Exception ignored) {
        }
        for (String key : testMultiKeys) {
            try {
                redisLock.unlock(key);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void testTryLockWithTimeout() {
        boolean locked = redisLock.tryLock(testKey, 5);
        assertTrue(locked, "应该能够获取锁");

        // 在另一个线程中尝试获取同一个锁
        boolean secondLocked = redisLock.tryLock(testKey, 1);
        assertFalse(secondLocked, "第二次获取同一个锁应该失败");

        // 释放锁
        redisLock.unlock(testKey);

        // 再次尝试获取锁
        boolean thirdLocked = redisLock.tryLock(testKey, 1);
        assertTrue(thirdLocked, "释放锁后应该能够再次获取锁");
        redisLock.unlock(testKey);
    }

    @Test
    void testLock() {
        redisLock.lock(testKey);

        // 在另一个线程中尝试获取同一个锁
        AtomicInteger result = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        executorService.submit(() -> {
            try {
                boolean locked = redisLock.tryLock(testKey, 1);
                result.set(locked ? 1 : 0);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(0, result.get(), "第二个线程应该无法获取锁");

        // 释放锁
        redisLock.unlock(testKey);
    }

    @Test
    void testTryLock() {
        boolean locked = redisLock.tryLock(testKey);
        assertTrue(locked, "应该能够获取锁");

        boolean secondLocked = redisLock.tryLock(testKey);
        assertFalse(secondLocked, "第二次获取同一个锁应该失败");

        redisLock.unlock(testKey);
    }

    @Test
    void testUnlockNonExistingLock() {
        // 尝试释放一个不存在的锁
        assertDoesNotThrow(() -> redisLock.unlock("non:existing:lock"),
                "释放不存在的锁不应抛出异常");
    }

    @Test
    void testMultiLock() {
        boolean locked = redisLock.multiLock(testMultiKeys);
        assertTrue(locked, "应该能够获取联锁");

        // 尝试获取其中一个锁
        boolean singleLocked = redisLock.tryLock(testMultiKeys.get(0), 1);
        assertFalse(singleLocked, "联锁中的单个锁应该无法获取");

        redisLock.unMultiLock(testMultiKeys);

        // 释放后应该可以获取单个锁
        boolean afterUnlock = redisLock.tryLock(testMultiKeys.get(0), 1);
        assertTrue(afterUnlock, "联锁释放后应该能够获取单个锁");
        redisLock.unlock(testMultiKeys.get(0));
    }

    @Test
    void testEmptyMultiLock() {
        assertThrows(LockException.class, () -> redisLock.multiLock(null),
                "空的联锁列表应该抛出异常");
        assertThrows(LockException.class, () -> redisLock.multiLock(Arrays.asList()),
                "空的联锁列表应该抛出异常");
    }

    @Test
    void testReadWriteLock() {
        // 获取读锁
        boolean readLocked = redisLock.tryReadLock(testKey, 1, 5, TimeUnit.SECONDS);
        assertTrue(readLocked, "应该能够获取读锁");

        // 另一个读锁应该可以获取
        boolean anotherReadLocked = redisLock.tryReadLock(testKey, 1, 5, TimeUnit.SECONDS);
        assertTrue(anotherReadLocked, "应该能够获取另一个读锁");

        // 写锁应该无法获取
        boolean writeLocked = redisLock.tryWriteLock(testKey, 1, 5, TimeUnit.SECONDS);
        assertFalse(writeLocked, "存在读锁时应该无法获取写锁");

        // 释放读锁
        redisLock.readLock(testKey).unlock();
        redisLock.readLock(testKey).unlock();

        // 现在应该可以获取写锁
        boolean afterReadUnlock = redisLock.tryWriteLock(testKey, 1, 5, TimeUnit.SECONDS);
        assertTrue(afterReadUnlock, "读锁释放后应该能够获取写锁");

        // 读锁应该无法获取
        boolean readAfterWrite = redisLock.tryReadLock(testKey, 1, 5, TimeUnit.SECONDS);
        assertFalse(readAfterWrite, "存在写锁时应该无法获取读锁");

        // 释放写锁
        redisLock.writeLock(testKey).unlock();
    }
}