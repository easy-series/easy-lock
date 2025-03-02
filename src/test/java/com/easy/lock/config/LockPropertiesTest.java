package com.easy.lock.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 锁配置属性测试
 */
@SpringBootTest
@ActiveProfiles("test")
class LockPropertiesTest {

    @Autowired
    private LockProperties lockProperties;

    @Test
    void testLockPropertiesLoaded() {
        assertNotNull(lockProperties, "锁配置属性不应为空");
        assertTrue(lockProperties.isEnabled(), "锁应该被启用");
        assertEquals("test-lock", lockProperties.getPrefix(), "锁前缀应该匹配配置");
        assertEquals(1000L, lockProperties.getWaitTime(), "等待时间应该匹配配置");
        assertEquals(5000L, lockProperties.getLeaseTime(), "租约时间应该匹配配置");
        assertEquals(TimeUnit.MILLISECONDS, lockProperties.getTimeUnit(), "时间单位应该匹配配置");
    }

    @Test
    void testRedisPropertiesLoaded() {
        LockProperties.Redis redis = lockProperties.getRedis();
        assertNotNull(redis, "Redis配置不应为空");
        assertTrue(redis.getAddress().startsWith("redis://"), "Redis地址应该以redis://开头");
        assertEquals(0, redis.getDatabase(), "Redis数据库索引应该匹配配置");
        assertEquals(8, redis.getPoolSize(), "连接池大小应该匹配配置");
        assertEquals(2, redis.getMinIdle(), "最小空闲连接数应该匹配配置");
        assertEquals(5000, redis.getConnectTimeout(), "连接超时时间应该匹配配置");
    }
}