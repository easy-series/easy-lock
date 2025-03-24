package com.easy.lock.sample.service;

import com.easy.lock.annotation.Lock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /**
     * 处理订单
     * 使用尝试获取锁，如果获取不到则抛出异常
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @Lock(key = "'order:' + #orderId", waitTime = 5000, leaseTime = 10000, timeUnit = TimeUnit.MILLISECONDS, failStrategy = Lock.FailStrategy.EXCEPTION)
    public String processOrder(String orderId) {
        log.info("开始处理订单: {}", orderId);
        try {
            // 模拟业务处理耗时
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(100, 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("订单处理完成: {}", orderId);
        return "订单: " + orderId + " 处理成功";
    }

    /**
     * 更新库存
     * 使用联锁，同时锁定商品和仓库
     *
     * @param productId   商品ID
     * @param warehouseId 仓库ID
     * @param quantity    数量
     * @return 更新结果
     */
    @Lock(type = Lock.LockType.MULTI_LOCK, keys = { "'product:' + #productId",
            "'warehouse:' + #warehouseId" }, waitTime = 3000, leaseTime = 5000, failStrategy = Lock.FailStrategy.EXCEPTION)
    public String updateStock(String productId, String warehouseId, int quantity) {
        log.info("开始更新库存: 商品={}, 仓库={}, 数量={}", productId, warehouseId, quantity);
        try {
            // 模拟业务处理耗时
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(300, 800));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("库存更新完成: 商品={}, 仓库={}, 数量={}", productId, warehouseId, quantity);
        return "库存更新成功: 商品=" + productId + ", 仓库=" + warehouseId + ", 数量=" + quantity;
    }

    /**
     * 查询订单
     * 使用读锁
     *
     * @param orderId 订单ID
     * @return 订单信息
     */
    @Lock(key = "'order:' + #orderId", mode = Lock.LockMode.READ, failStrategy = Lock.FailStrategy.RETURN_NULL)
    public String queryOrder(String orderId) {
        log.info("开始查询订单: {}", orderId);
        try {
            // 模拟业务处理耗时
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(50, 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("订单查询完成: {}", orderId);
        return "订单: " + orderId + " 查询结果";
    }

    /**
     * 演示重试功能
     * 使用尝试获取锁，启用重试机制
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @Lock(key = "'order:retry:' + #orderId", waitTime = 1000, leaseTime = 5000, failStrategy = Lock.FailStrategy.EXCEPTION, retryEnabled = true, maxRetries = 3, retryInterval = 1000, retryStrategy = Lock.RetryStrategy.EXPONENTIAL)
    public String processOrderWithRetry(String orderId) {
        log.info("开始处理订单(带重试): {}", orderId);
        try {
            // 模拟业务处理耗时
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(100, 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("订单处理完成(带重试): {}", orderId);
        return "订单: " + orderId + " 处理成功(带重试)";
    }

    /**
     * 演示可重入锁功能
     * 外部方法获取锁
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @Lock(key = "'order:reentrant:' + #orderId", waitTime = 3000, leaseTime = 10000)
    public String processOrderWithReentrant(String orderId) {
        log.info("外部方法开始处理订单: {}", orderId);

        // 调用内部方法，该方法也会获取同一把锁
        String result = processOrderDetail(orderId);

        log.info("外部方法处理完成: {}", orderId);
        return "订单: " + orderId + " 外部处理成功, 内部结果: " + result;
    }

    /**
     * 内部方法，也会获取同一把锁
     * 由于是同一线程访问同一把锁，所以会重入成功
     */
    @Lock(key = "'order:reentrant:' + #orderId", waitTime = 3000, leaseTime = 10000)
    public String processOrderDetail(String orderId) {
        log.info("内部方法处理订单详情: {}", orderId);
        try {
            // 模拟业务处理耗时
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(100, 300));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("内部方法处理完成: {}", orderId);
        return "订单详情处理成功";
    }
}