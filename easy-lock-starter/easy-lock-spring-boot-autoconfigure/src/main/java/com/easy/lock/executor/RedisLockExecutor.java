package com.easy.lock.executor;

import com.easy.lock.core.LockExecutor;
import com.easy.lock.exception.LockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的锁执行器实现
 */
@Slf4j
@RequiredArgsConstructor
public class RedisLockExecutor implements LockExecutor {

    private final RedissonClient redissonClient;

    @Override
    public LockType getLockType() {
        return LockType.REDIS;
    }

    @Override
    public boolean tryLock(String lockKey, String lockValue, long waitTime, long leaseTime, TimeUnit timeUnit) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (acquired) {
                log.debug("获取锁成功: key={}, value={}", lockKey, lockValue);
            } else {
                log.debug("获取锁失败: key={}, value={}", lockKey, lockValue);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁被中断: key={}, value={}", lockKey, lockValue, e);
            return false;
        } catch (Exception e) {
            log.error("获取锁异常: key={}, value={}", lockKey, lockValue, e);
            throw new LockException("获取锁异常: " + e.getMessage(), e);
        }
    }

    @Override
    public void lock(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (leaseTime > 0) {
                lock.lock(leaseTime, timeUnit);
            } else {
                lock.lock();
            }
            log.debug("获取锁成功: key={}, value={}", lockKey, lockValue);
        } catch (Exception e) {
            log.error("获取锁异常: key={}, value={}", lockKey, lockValue, e);
            throw new LockException("获取锁异常: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean releaseLock(String lockKey, String lockValue) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放锁成功: key={}, value={}", lockKey, lockValue);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("释放锁异常: key={}, value={}", lockKey, lockValue, e);
            return false;
        }
    }

    @Override
    public boolean tryMultiLock(List<String> lockKeys, String lockValue, long waitTime, long leaseTime,
            TimeUnit timeUnit) {
        try {
            RLock[] locks = lockKeys.stream()
                    .map(redissonClient::getLock)
                    .toArray(RLock[]::new);

            RLock multiLock = redissonClient.getMultiLock(locks);
            boolean acquired = multiLock.tryLock(waitTime, leaseTime, timeUnit);

            if (acquired) {
                log.debug("获取联锁成功: keys={}, value={}", lockKeys, lockValue);
            } else {
                log.debug("获取联锁失败: keys={}, value={}", lockKeys, lockValue);
            }

            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取联锁被中断: keys={}, value={}", lockKeys, lockValue, e);
            return false;
        } catch (Exception e) {
            log.error("获取联锁异常: keys={}, value={}", lockKeys, lockValue, e);
            throw new LockException("获取联锁异常: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean releaseMultiLock(List<String> lockKeys, String lockValue) {
        try {
            RLock[] locks = lockKeys.stream()
                    .map(redissonClient::getLock)
                    .toArray(RLock[]::new);

            RLock multiLock = redissonClient.getMultiLock(locks);
            if (multiLock.isLocked() && multiLock.isHeldByCurrentThread()) {
                multiLock.unlock();
                log.debug("释放联锁成功: keys={}, value={}", lockKeys, lockValue);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("释放联锁异常: keys={}, value={}", lockKeys, lockValue, e);
            return false;
        }
    }
}