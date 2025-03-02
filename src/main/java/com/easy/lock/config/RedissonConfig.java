package com.easy.lock.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.easy.lock.api.RedisLock;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(LockProperties.class)
@ConditionalOnProperty(prefix = "easy.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedissonConfig {

    private final LockProperties lockProperties;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        LockProperties.Redis redis = lockProperties.getRedis();

        // 单节点模式
        config.useSingleServer()
                .setAddress(redis.getAddress())
                .setDatabase(redis.getDatabase())
                .setConnectionPoolSize(redis.getPoolSize())
                .setConnectionMinimumIdleSize(redis.getMinIdle())
                .setConnectTimeout(redis.getConnectTimeout());

        // 设置密码（如果有）
        if (StringUtils.hasText(redis.getPassword())) {
            config.useSingleServer().setPassword(redis.getPassword());
        }

        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnClass(RedissonClient.class)
    public RedisLock redisLock(RedissonClient redissonClient) {
        return new RedisLock(redissonClient);
    }
}