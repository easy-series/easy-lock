package com.easy.lock.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 锁异常测试
 */
class LockExceptionTest {

    private static final String TEST_MESSAGE = "测试异常消息";
    private static final String TEST_KEY = "test:key";
    private static final Exception TEST_CAUSE = new RuntimeException("测试原因");

    @Test
    void testLockException() {
        // 测试消息构造函数
        LockException exception1 = new LockException(TEST_MESSAGE);
        assertEquals(TEST_MESSAGE, exception1.getMessage(), "异常消息应该匹配");
        assertNull(exception1.getCause(), "原因应该为空");

        // 测试消息和原因构造函数
        LockException exception2 = new LockException(TEST_MESSAGE, TEST_CAUSE);
        assertEquals(TEST_MESSAGE, exception2.getMessage(), "异常消息应该匹配");
        assertEquals(TEST_CAUSE, exception2.getCause(), "原因应该匹配");

        // 测试原因构造函数
        LockException exception3 = new LockException(TEST_CAUSE);
        assertEquals(TEST_CAUSE, exception3.getCause(), "原因应该匹配");
    }

    @Test
    void testLockAcquireFailedException() {
        // 测试key构造函数
        LockAcquireFailedException exception1 = new LockAcquireFailedException(TEST_KEY);
        assertEquals("获取锁失败: " + TEST_KEY, exception1.getMessage(), "异常消息应该匹配");
        assertEquals(TEST_KEY, exception1.getLockKey(), "锁key应该匹配");
        assertNull(exception1.getCause(), "原因应该为空");

        // 测试key和原因构造函数
        LockAcquireFailedException exception2 = new LockAcquireFailedException(TEST_KEY, TEST_CAUSE);
        assertEquals("获取锁失败: " + TEST_KEY, exception2.getMessage(), "异常消息应该匹配");
        assertEquals(TEST_KEY, exception2.getLockKey(), "锁key应该匹配");
        assertEquals(TEST_CAUSE, exception2.getCause(), "原因应该匹配");
    }

    @Test
    void testExceptionHierarchy() {
        // 测试异常继承关系
        LockAcquireFailedException exception = new LockAcquireFailedException(TEST_KEY);
        assertTrue(exception instanceof LockException, "LockAcquireFailedException应该是LockException的子类");
        assertTrue(exception instanceof RuntimeException, "LockException应该是RuntimeException的子类");
    }
}