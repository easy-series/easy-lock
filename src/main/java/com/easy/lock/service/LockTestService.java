package com.easy.lock.service;

import com.easy.lock.annotation.Lock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LockTestService {
    
    @Lock(prefix = "test", key = "#key", type = Lock.LockType.TRY_LOCK)
    public void tryLockMethod(String key) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("线程已被中断");
        }
        log.info("执行tryLock方法，key: {}", key);
        Thread.sleep(100); // 模拟业务处理
    }

    @Lock(prefix = "test", key = "#key", type = Lock.LockType.LOCK)
    public void lockMethod(String key) throws InterruptedException {
        log.info("执行lock方法，key: {}", key);
        Thread.sleep(100); // 模拟业务处理
    }

    @Lock(
        prefix = "test",
        keys = {"#productId", "#warehouseId"},
        type = Lock.LockType.MULTI_LOCK
    )
    public void multiLockMethod(String productId, String warehouseId) throws InterruptedException {
        log.info("执行multiLock方法，productId: {}, warehouseId: {}", productId, warehouseId);
        Thread.sleep(100); // 模拟业务处理
    }

    @Lock(
        prefix = "order",
        key = "#orderId + ':' + #amount",
        type = Lock.LockType.TRY_LOCK,
        waitTime = 1000,
        leaseTime = 5000
    )
    public void spELLockMethod(String orderId, int amount) throws InterruptedException {
        log.info("执行spELLock方法，orderId: {}, amount: {}", orderId, amount);
        Thread.sleep(2000); // 模拟业务处理
    }
} 