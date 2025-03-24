package com.easy.lock.executor;

import com.easy.lock.autoconfigure.EasyLockProperties;
import com.easy.lock.core.LockExecutor;
import com.easy.lock.exception.LockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于数据库的锁执行器实现
 */
@Slf4j
public class JdbcLockExecutor implements LockExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final EasyLockProperties.Jdbc jdbcProperties;
    private final String tableName;

    public JdbcLockExecutor(DataSource dataSource, EasyLockProperties lockProperties) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcProperties = lockProperties.getJdbc();
        this.tableName = jdbcProperties.getTableName();
        if (jdbcProperties.isCreateTable()) {
            initTable();
        }
    }

    @PostConstruct
    public void initTable() {
        try {
            // 创建锁表
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "lock_key VARCHAR(255) PRIMARY KEY, " +
                    "lock_value VARCHAR(255) NOT NULL, " +
                    "create_time TIMESTAMP NOT NULL, " +
                    "expire_time TIMESTAMP NOT NULL" +
                    ")";
            jdbcTemplate.execute(createTableSql);
            log.info("分布式锁表创建成功: {}", tableName);
        } catch (Exception e) {
            log.error("创建分布式锁表失败", e);
            throw new LockException("创建分布式锁表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public LockType getLockType() {
        return LockType.JDBC;
    }

    @Override
    public boolean tryLock(String lockKey, String lockValue, long waitTime, long leaseTime, TimeUnit timeUnit) {
        long startTime = System.currentTimeMillis();
        long waitTimeMillis = timeUnit.toMillis(waitTime);
        long leaseTimeMillis = timeUnit.toMillis(leaseTime > 0 ? leaseTime : jdbcProperties.getExpireTime());

        // 清理过期的锁
        cleanExpiredLock(lockKey);

        while (true) {
            try {
                boolean acquired = doLock(lockKey, lockValue, leaseTimeMillis);
                if (acquired) {
                    log.debug("获取锁成功: key={}, value={}", lockKey, lockValue);
                    return true;
                }

                // 检查是否超时
                if (System.currentTimeMillis() - startTime > waitTimeMillis) {
                    log.debug("获取锁超时: key={}, value={}", lockKey, lockValue);
                    return false;
                }

                // 等待一段时间后重试
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("获取锁被中断: key={}, value={}", lockKey, lockValue, e);
                return false;
            } catch (Exception e) {
                log.error("获取锁异常: key={}, value={}", lockKey, lockValue, e);
                throw new LockException("获取锁异常: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void lock(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit) {
        long leaseTimeMillis = timeUnit.toMillis(leaseTime > 0 ? leaseTime : jdbcProperties.getExpireTime());

        // 清理过期的锁
        cleanExpiredLock(lockKey);

        while (true) {
            try {
                boolean acquired = doLock(lockKey, lockValue, leaseTimeMillis);
                if (acquired) {
                    log.debug("获取锁成功: key={}, value={}", lockKey, lockValue);
                    return;
                }

                // 等待一段时间后重试
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("获取锁被中断: key={}, value={}", lockKey, lockValue, e);
                throw new LockException("获取锁被中断", e);
            } catch (Exception e) {
                log.error("获取锁异常: key={}, value={}", lockKey, lockValue, e);
                throw new LockException("获取锁异常: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean releaseLock(String lockKey, String lockValue) {
        try {
            String sql = "DELETE FROM " + tableName + " WHERE lock_key = ? AND lock_value = ?";
            int rows = jdbcTemplate.update(sql, lockKey, lockValue);
            boolean released = rows > 0;
            if (released) {
                log.debug("释放锁成功: key={}, value={}", lockKey, lockValue);
            } else {
                log.debug("释放锁失败，锁不存在或已被其他线程释放: key={}, value={}", lockKey, lockValue);
            }
            return released;
        } catch (Exception e) {
            log.error("释放锁异常: key={}, value={}", lockKey, lockValue, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean tryMultiLock(List<String> lockKeys, String lockValue, long waitTime, long leaseTime,
            TimeUnit timeUnit) {
        long startTime = System.currentTimeMillis();
        long waitTimeMillis = timeUnit.toMillis(waitTime);
        long leaseTimeMillis = timeUnit.toMillis(leaseTime > 0 ? leaseTime : jdbcProperties.getExpireTime());

        // 按照锁的key的字典顺序排序，防止死锁
        lockKeys.sort(String::compareTo);

        while (true) {
            // 清理所有过期的锁
            for (String lockKey : lockKeys) {
                cleanExpiredLock(lockKey);
            }

            boolean allAcquired = true;
            try {
                for (String lockKey : lockKeys) {
                    boolean acquired = doLock(lockKey, lockValue, leaseTimeMillis);
                    if (!acquired) {
                        allAcquired = false;
                        // 释放已获取的锁
                        for (String key : lockKeys) {
                            if (key.equals(lockKey)) {
                                break;
                            }
                            releaseLock(key, lockValue);
                        }
                        break;
                    }
                }

                if (allAcquired) {
                    log.debug("获取联锁成功: keys={}, value={}", lockKeys, lockValue);
                    return true;
                }

                // 检查是否超时
                if (System.currentTimeMillis() - startTime > waitTimeMillis) {
                    log.debug("获取联锁超时: keys={}, value={}", lockKeys, lockValue);
                    return false;
                }

                // 等待一段时间后重试
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("获取联锁被中断: keys={}, value={}", lockKeys, lockValue, e);
                return false;
            } catch (Exception e) {
                log.error("获取联锁异常: keys={}, value={}", lockKeys, lockValue, e);
                throw new LockException("获取联锁异常: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean releaseMultiLock(List<String> lockKeys, String lockValue) {
        boolean allReleased = true;
        for (String lockKey : lockKeys) {
            boolean released = releaseLock(lockKey, lockValue);
            if (!released) {
                allReleased = false;
            }
        }
        return allReleased;
    }

    /**
     * 执行锁定操作
     *
     * @param lockKey         锁的key
     * @param lockValue       锁的值
     * @param leaseTimeMillis 持有锁的时间（毫秒）
     * @return 是否获取成功
     */
    private boolean doLock(String lockKey, String lockValue, long leaseTimeMillis) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusNanos(TimeUnit.MILLISECONDS.toNanos(leaseTimeMillis));

        try {
            String sql = "INSERT INTO " + tableName
                    + " (lock_key, lock_value, create_time, expire_time) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(sql, lockKey, lockValue, Timestamp.valueOf(now), Timestamp.valueOf(expireTime));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        } catch (DataAccessException e) {
            log.error("数据库访问异常: key={}, value={}", lockKey, lockValue, e);
            return false;
        }
    }

    /**
     * 清理过期的锁
     *
     * @param lockKey 锁的key
     */
    private void cleanExpiredLock(String lockKey) {
        try {
            String sql = "DELETE FROM " + tableName + " WHERE lock_key = ? AND expire_time < ?";
            jdbcTemplate.update(sql, lockKey, Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.error("清理过期锁异常: key={}", lockKey, e);
        }
    }
}