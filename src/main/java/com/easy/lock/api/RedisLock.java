package com.easy.lock.api;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

import com.easy.lock.exception.LockException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisLock {

    private final RedissonClient redissonClient;

    public RedisLock(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 互斥锁，seconds秒后自动失效
     *
     * @param key     锁的key
     * @param seconds 锁的超时时间（秒）
     * @return 是否获取锁成功
     * @throws LockException 获取锁过程中发生异常
     */
    public boolean tryLock(String key, int seconds) {
        RLock rLock = redissonClient.getLock(key);
        try {
            return rLock.tryLock(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockException("获取锁被中断: " + key, e);
        }
    }

    /**
     * 互斥锁，自动续期
     *
     * @param key 锁的key
     * @throws LockException 获取锁过程中发生异常
     */
    public void lock(String key) {
        RLock rLock = redissonClient.getLock(key);
        try {
            rLock.lock();
            log.debug("获取锁成功: {}", key);
        } catch (Exception e) {
            throw new LockException("获取锁失败: " + key, e);
        }
    }

    /**
     * 互斥锁，尝试获取锁（不带超时）
     *
     * @param key 锁的key
     * @return 是否获取锁成功
     * @throws LockException 获取锁过程中发生异常
     */
    public boolean tryLock(String key) {
        RLock rLock = redissonClient.getLock(key);
        try {
            boolean locked = rLock.tryLock();
            log.debug("tryLock: key={}, locked={}", key, locked);
            return locked;
        } catch (Exception e) {
            throw new LockException("尝试获取锁失败: " + key, e);
        }
    }

    /**
     * 手动释放锁
     *
     * @param key 锁的key
     */
    public void unlock(String key) {
        RLock rLock = redissonClient.getLock(key);
        try {
            if (rLock.isLocked() && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                log.debug("unlock: key={}", key);
            } else {
                log.warn("尝试释放未持有的锁: {}", key);
            }
        } catch (Exception e) {
            log.error("释放锁异常: {}", key, e);
            throw new LockException("释放锁异常: " + key, e);
        }
    }

    /**
     * 联锁 加锁
     * 
     * @param redisKeyList 锁的key列表
     * @return 是否获取锁成功
     * @throws LockException 获取锁过程中发生异常
     */
    public boolean multiLock(List<String> redisKeyList) {
        if (redisKeyList == null || redisKeyList.isEmpty()) {
            throw new LockException("联锁的key列表不能为空");
        }

        try {
            RLock multiLock = getMultiLock(redisKeyList);
            boolean locked = multiLock.tryLock();
            log.debug("multiLock: keys={}, locked={}", redisKeyList, locked);
            return locked;
        } catch (Exception e) {
            throw new LockException("获取联锁失败: " + String.join(",", redisKeyList), e);
        }
    }

    private RLock getMultiLock(List<String> redisKeyList) {
        RLock[] locks = new RLock[redisKeyList.size()];
        for (int i = 0; i < redisKeyList.size(); i++) {
            RLock lock = redissonClient.getLock(redisKeyList.get(i));
            locks[i] = lock;
        }
        return redissonClient.getMultiLock(locks);
    }

    /**
     * 联锁 解锁
     * 
     * @param redisKeyList 锁的key列表
     * @throws LockException 释放锁过程中发生异常
     */
    public void unMultiLock(List<String> redisKeyList) {
        if (redisKeyList == null || redisKeyList.isEmpty()) {
            throw new LockException("联锁的key列表不能为空");
        }

        try {
            RLock multiLock = getMultiLock(redisKeyList);
            if (multiLock.isHeldByCurrentThread()) {
                multiLock.unlock();
                log.debug("unMultiLock: keys={}", redisKeyList);
            } else {
                log.warn("尝试释放未持有的联锁: {}", redisKeyList);
            }
        } catch (Exception e) {
            log.error("释放联锁异常: {}", redisKeyList, e);
            throw new LockException("释放联锁异常: " + String.join(",", redisKeyList), e);
        }
    }

    /**
     * 获取读锁
     *
     * @param key 锁的key
     * @return 读锁
     */
    public RLock readLock(String key) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(key);
        return rwLock.readLock();
    }

    /**
     * 获取写锁
     *
     * @param key 锁的key
     * @return 写锁
     */
    public RLock writeLock(String key) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(key);
        return rwLock.writeLock();
    }

    /**
     * 尝试获取读锁
     *
     * @param key       锁的key
     * @param waitTime  等待时间
     * @param leaseTime 持有时间
     * @param unit      时间单位
     * @return 是否获取锁成功
     */
    public boolean tryReadLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
        RLock readLock = readLock(key);
        try {
            boolean locked = readLock.tryLock(waitTime, leaseTime, unit);
            log.debug("tryReadLock: key={}, locked={}", key, locked);
            return locked;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockException("获取读锁被中断: " + key, e);
        } catch (Exception e) {
            throw new LockException("尝试获取读锁失败: " + key, e);
        }
    }

    /**
     * 尝试获取写锁
     *
     * @param key       锁的key
     * @param waitTime  等待时间
     * @param leaseTime 持有时间
     * @param unit      时间单位
     * @return 是否获取锁成功
     */
    public boolean tryWriteLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
        RLock writeLock = writeLock(key);
        try {
            boolean locked = writeLock.tryLock(waitTime, leaseTime, unit);
            log.debug("tryWriteLock: key={}, locked={}", key, locked);
            return locked;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockException("获取写锁被中断: " + key, e);
        } catch (Exception e) {
            throw new LockException("尝试获取写锁失败: " + key, e);
        }
    }
}
