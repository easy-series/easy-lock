package com.easy.lock.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.easy.lock.AbstractIntegrationTest;
import com.easy.lock.api.RedisLock;

/**
 * Redisson配置测试
 */
class RedissonConfigTest extends AbstractIntegrationTest {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisLock redisLock;

    @Test
    void testRedissonClientCreated() {
        assertNotNull(redissonClient, "RedissonClient应该被创建");
        assertTrue(redissonClient.getConfig().useSingleServer().getAddress().startsWith("redis://"),
                "RedissonClient应该使用单节点模式");
    }

    @Test
    void testRedisLockCreated() {
        assertNotNull(redisLock, "RedisLock应该被创建");
    }
}