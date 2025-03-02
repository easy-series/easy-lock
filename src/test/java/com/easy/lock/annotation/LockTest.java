package com.easy.lock.annotation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * 锁注解测试
 */
class LockTest {

    @Test
    void testLockAnnotationDefaults() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("defaultMethod");
        Lock lock = method.getAnnotation(Lock.class);

        assertNotNull(lock, "Lock注解不应为空");
        assertEquals("", lock.prefix(), "默认前缀应为空字符串");
        assertEquals("", lock.key(), "默认key应为空字符串");
        assertEquals(0, lock.keys().length, "默认keys应为空数组");
        assertEquals(Lock.LockType.TRY_LOCK, lock.type(), "默认类型应为TRY_LOCK");
        assertEquals(Lock.LockMode.WRITE, lock.mode(), "默认模式应为WRITE");
        assertEquals(-1L, lock.waitTime(), "默认等待时间应为-1");
        assertEquals(-1L, lock.leaseTime(), "默认租约时间应为-1");
        assertEquals(TimeUnit.MILLISECONDS, lock.timeUnit(), "默认时间单位应为MILLISECONDS");
    }

    @Test
    void testLockAnnotationCustomValues() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("customMethod");
        Lock lock = method.getAnnotation(Lock.class);

        assertNotNull(lock, "Lock注解不应为空");
        assertEquals("custom", lock.prefix(), "前缀应匹配");
        assertEquals("#param", lock.key(), "key应匹配");
        assertArrayEquals(new String[] { "#key1", "#key2" }, lock.keys(), "keys应匹配");
        assertEquals(Lock.LockType.LOCK, lock.type(), "类型应匹配");
        assertEquals(Lock.LockMode.READ, lock.mode(), "模式应匹配");
        assertEquals(5000L, lock.waitTime(), "等待时间应匹配");
        assertEquals(10000L, lock.leaseTime(), "租约时间应匹配");
        assertEquals(TimeUnit.SECONDS, lock.timeUnit(), "时间单位应匹配");
    }

    @Test
    void testLockTypeEnum() {
        assertEquals(3, Lock.LockType.values().length, "LockType应有3个枚举值");
        assertEquals(Lock.LockType.TRY_LOCK, Lock.LockType.valueOf("TRY_LOCK"), "应能获取TRY_LOCK枚举值");
        assertEquals(Lock.LockType.LOCK, Lock.LockType.valueOf("LOCK"), "应能获取LOCK枚举值");
        assertEquals(Lock.LockType.MULTI_LOCK, Lock.LockType.valueOf("MULTI_LOCK"), "应能获取MULTI_LOCK枚举值");
    }

    @Test
    void testLockModeEnum() {
        assertEquals(2, Lock.LockMode.values().length, "LockMode应有2个枚举值");
        assertEquals(Lock.LockMode.WRITE, Lock.LockMode.valueOf("WRITE"), "应能获取WRITE枚举值");
        assertEquals(Lock.LockMode.READ, Lock.LockMode.valueOf("READ"), "应能获取READ枚举值");
    }

    /**
     * 测试类
     */
    static class TestClass {

        @Lock
        public void defaultMethod() {
            // 测试默认值
        }

        @Lock(prefix = "custom", key = "#param", keys = { "#key1",
                "#key2" }, type = Lock.LockType.LOCK, mode = Lock.LockMode.READ, waitTime = 5000, leaseTime = 10000, timeUnit = TimeUnit.SECONDS)
        public void customMethod() {
            // 测试自定义值
        }
    }
}