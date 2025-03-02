package com.easy.lock.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 分布式锁配置属性
 */
@Data
@ConfigurationProperties(prefix = "easy.lock")
public class LockProperties {

    /**
     * 是否启用分布式锁
     */
    private boolean enabled = true;

    /**
     * 锁的默认前缀
     */
    private String prefix = "lock";

    /**
     * 默认等待获取锁的时间（毫秒）
     */
    private long waitTime = 3000L;

    /**
     * 默认持有锁的时间（毫秒）
     */
    private long leaseTime = 30000L;

    /**
     * 默认时间单位
     */
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    /**
     * Redis配置
     */
    private Redis redis = new Redis();

    @Data
    public static class Redis {
        /**
         * Redis地址，格式：redis://host:port
         */
        private String address = "redis://127.0.0.1:6379";

        /**
         * Redis密码
         */
        private String password;

        /**
         * Redis数据库索引
         */
        private int database = 0;

        /**
         * 连接池最大连接数
         */
        private int poolSize = 64;

        /**
         * 连接池最小空闲连接数
         */
        private int minIdle = 24;

        /**
         * 连接超时时间（毫秒）
         */
        private int connectTimeout = 10000;
    }
}