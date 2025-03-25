package com.caoyixin.lock.starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 锁配置属性类
 *
 * @author caoyixin
 */
@Data
@ConfigurationProperties(prefix = "cyx-lock")
public class LockProperties {

    /**
     * 锁键前缀
     */
    private String lockKeyPrefix = "cyx:lock";

    /**
     * 获取锁超时时间，单位：毫秒
     */
    private long acquireTimeout = 3000;

    /**
     * 锁过期时间，单位：毫秒
     */
    private long expire = 30000;

    /**
     * 是否启用监控指标
     */
    private boolean metricsEnabled = true;
} 