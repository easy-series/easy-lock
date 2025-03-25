package com.caoyixin.lock.core;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 锁信息，保存锁的基本信息
 *
 * @author caoyixin
 */
@Data
@Accessors(chain = true)
public class LockInfo {

    /**
     * 锁的key
     */
    private String key;

    /**
     * 锁的值，用于标识锁的持有者
     */
    private String value;

    /**
     * 锁的过期时间戳
     */
    private Long expireTime;

    /**
     * 重入计数
     */
    private Integer reentrantCount = 0;

    /**
     * 加锁时间
     */
    private Long lockedAt;

    /**
     * 锁的状态
     */
    private LockState state = LockState.UNLOCKED;

    /**
     * 获取锁耗时(毫秒)
     */
    private Long acquireTime;

    /**
     * 锁的名称
     */
    private String name;

    /**
     * 锁的状态枚举
     */
    public enum LockState {
        /**
         * 已锁定
         */
        LOCKED,
        /**
         * 未锁定
         */
        UNLOCKED
    }
}