package com.easy.lock.config;

import com.easy.lock.autoconfigure.EasyLockProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置类
 */
@Slf4j
@Configuration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "easy.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedissonConfig {

    /**
     * 创建RedissonClient
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "easy.lock", name = "lock-type", havingValue = "REDIS")
    public RedissonClient redissonClient(EasyLockProperties lockProperties) {
        log.info("初始化RedissonClient");

        try {
            Config config = new Config();
            EasyLockProperties.Redis redisProps = lockProperties.getRedis();

            // 从原始地址中提取主机和端口
            String address = redisProps.getAddress();
            if (!address.startsWith("redis://")) {
                // 如果不是以redis://开头，则使用配置的host和port
                address = "redis://" + redisProps.getAddress();
            }

            String password = redisProps.getPassword();
            int database = redisProps.getDatabase();

            log.info("Redis配置: address={}, database={}, hasPassword={}",
                    address, database, StringUtils.isNotBlank(password));

            // 配置单节点模式
            config.useSingleServer()
                    .setAddress(address)
                    .setDatabase(database);

            // 设置密码(如果有)
            if (StringUtils.isNotBlank(password)) {
                config.useSingleServer().setPassword(password);
                log.info("已设置Redis密码");
            }

            log.info("尝试连接Redis服务器: {}", address);
            return Redisson.create(config);
        } catch (Exception e) {
            log.error("创建RedissonClient失败", e);
            throw e;
        }
    }
}