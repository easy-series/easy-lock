package com.easy.lock.example;

import com.easy.lock.annotation.Lock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LockExample {

    @Lock(prefix = "order", key = "#orderId")
    public void processOrder(String orderId) {
        log.info("开始处理订单: {}", orderId);
        try {
            // 模拟业务处理
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("订单处理完成: {}", orderId);
    }

    @Lock(prefix = "stock", key = "#productId", waitTime = 5000, leaseTime = 10000)
    public void updateStock(String productId, int quantity) {
        log.info("开始更新库存，商品ID: {}, 数量: {}", productId, quantity);
        try {
            // 模拟业务处理
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("库存更新完成，商品ID: {}", productId);
    }
} 