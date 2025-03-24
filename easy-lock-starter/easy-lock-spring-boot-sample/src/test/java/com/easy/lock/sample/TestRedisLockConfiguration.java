package com.easy.lock.sample;

import com.easy.lock.core.LockExecutor;
import com.easy.lock.executor.RedisLockExecutor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试用Redis锁执行器配置
 * 确保测试环境中有可用的Redis锁执行器
 */
@Slf4j
@TestConfiguration
public class TestRedisLockConfiguration {

    /**
     * 创建测试用RedissonClient
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public RedissonClient testRedissonClient() {
        log.info("创建测试用RedissonClient");
        try {
            // 创建Redisson配置
            Config config = new Config();
            config.useSingleServer()
                    .setAddress("redis://localhost:6379")
                    .setPassword("123456")
                    .setDatabase(0)
                    .setConnectionMinimumIdleSize(1)
                    .setConnectionPoolSize(2);
            
            return Redisson.create(config);
        } catch (Exception e) {
            log.error("创建测试用RedissonClient失败", e);
            throw e;
        }
    }
    
    /**
     * 创建测试用RedisLockExecutor
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "redisLockExecutor")
    public LockExecutor redisLockExecutor(RedissonClient redissonClient) {
        log.info("创建测试用RedisLockExecutor");
        return new RedisLockExecutor(redissonClient);
    }
} 