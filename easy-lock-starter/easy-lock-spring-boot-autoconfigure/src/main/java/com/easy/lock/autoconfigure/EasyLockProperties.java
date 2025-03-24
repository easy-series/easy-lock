package com.easy.lock.autoconfigure;

import com.easy.lock.core.LockExecutor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁配置属性
 */
@Data
@ConfigurationProperties(prefix = "easy.lock")
public class EasyLockProperties {

    /**
     * 是否启用分布式锁
     */
    private boolean enabled = true;

    /**
     * 锁的key前缀
     */
    private String prefix = "lock";

    /**
     * 等待获取锁的时间（毫秒）
     */
    private long waitTime = 3000L;

    /**
     * 持有锁的时间（毫秒）
     */
    private long leaseTime = 30000L;

    /**
     * 时间单位
     */
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    /**
     * 锁类型，默认使用Redis
     */
    private LockExecutor.LockType lockType = LockExecutor.LockType.REDIS;

    /**
     * Redis配置
     */
    private Redis redis = new Redis();

    /**
     * 数据库配置
     */
    private Jdbc jdbc = new Jdbc();

    @Data
    public static class Redis {
        /**
         * Redis地址
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
         * 连接池大小
         */
        private int poolSize = 64;

        /**
         * 最小空闲连接数
         */
        private int minIdle = 24;

        /**
         * 连接超时时间（毫秒）
         */
        private int connectTimeout = 10000;
    }

    @Data
    public static class Jdbc {
        /**
         * 数据库表名
         */
        private String tableName = "distributed_lock";

        /**
         * 是否自动创建表
         */
        private boolean createTable = true;

        /**
         * 锁的最大有效时间（毫秒），超过该时间的锁将被自动释放
         */
        private long expireTime = 60000L;
    }
}