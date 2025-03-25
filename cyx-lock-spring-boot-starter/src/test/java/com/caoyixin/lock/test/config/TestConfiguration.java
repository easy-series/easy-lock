package com.caoyixin.lock.test.config;

import com.caoyixin.lock.core.LockExecutor;
import com.caoyixin.lock.redisson.executor.RedissonLockExecutor;
import com.caoyixin.lock.starter.autoconfigure.LockAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 测试配置类，提供测试环境所需的所有bean
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan("com.caoyixin.lock.test.service")
@Import(LockAutoConfiguration.class)
public class TestConfiguration {

    /**
     * 提供测试使用的度量注册表
     */
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * 提供Redis客户端
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379").setPassword("123456");
        return Redisson.create(config);
    }

    /**
     * 提供基于Redisson的锁执行器
     */
    @Bean
    public LockExecutor lockExecutor(RedissonClient redissonClient) {
        RedissonLockExecutor executor = new RedissonLockExecutor();
        executor.setRedissonClient(redissonClient);
        return executor;
    }

}