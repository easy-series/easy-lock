package com.easy.lock.sample;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis连接测试
 */
@SpringBootTest
@Slf4j
@Import({ RedisTestConfiguration.class, TestRedisLockConfiguration.class })
public class RedisConnectionTest {

    @Autowired
    private RedissonClient redissonClient;

    @Test
    @DisplayName("测试Redis连接是否正常")
    public void testRedisConnection() {
        log.info("开始测试Redis连接...");
        assertNotNull(redissonClient, "RedissonClient不应该为空");

        // 测试基本的Redis操作
        String testKey = "test:connection:" + System.currentTimeMillis();
        String testValue = "测试值";

        try {
            RBucket<String> bucket = redissonClient.getBucket(testKey);
            bucket.set(testValue);

            String retrievedValue = bucket.get();
            assertEquals(testValue, retrievedValue, "存储和获取的值应该相同");

            log.info("Redis连接测试成功！");
        } catch (Exception e) {
            log.error("Redis连接测试失败", e);
            throw e;
        }
    }
}