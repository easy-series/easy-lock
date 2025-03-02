package com.easy.lock;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.easy.lock.api.RedisLock;
import com.easy.lock.config.LockProperties;
import com.easy.lock.monitor.LockMetrics;

/**
 * 应用程序集成测试
 */
@SpringBootTest
class EasyLockApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // 验证应用上下文加载成功
        assertNotNull(applicationContext, "应用上下文不应为空");

        // 验证关键Bean加载成功
        assertNotNull(applicationContext.getBean(RedissonClient.class), "RedissonClient Bean不应为空");
        assertNotNull(applicationContext.getBean(RedisLock.class), "RedisLock Bean不应为空");
        assertNotNull(applicationContext.getBean(LockProperties.class), "LockProperties Bean不应为空");
        assertNotNull(applicationContext.getBean(LockMetrics.class), "LockMetrics Bean不应为空");
    }
}
