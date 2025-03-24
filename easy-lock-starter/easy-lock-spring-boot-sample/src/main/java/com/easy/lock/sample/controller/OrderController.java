package com.easy.lock.sample.controller;

import com.easy.lock.sample.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    /**
     * 处理订单
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @GetMapping("/process/{orderId}")
    public String processOrder(@PathVariable String orderId) {
        try {
            return orderService.processOrder(orderId);
        } catch (Exception e) {
            log.error("处理订单失败: {}", orderId, e);
            return "处理订单失败: " + e.getMessage();
        }
    }

    /**
     * 更新库存
     *
     * @param productId   商品ID
     * @param warehouseId 仓库ID
     * @param quantity    数量
     * @return 更新结果
     */
    @GetMapping("/stock/{productId}/{warehouseId}/{quantity}")
    public String updateStock(@PathVariable String productId,
            @PathVariable String warehouseId,
            @PathVariable int quantity) {
        try {
            return orderService.updateStock(productId, warehouseId, quantity);
        } catch (Exception e) {
            log.error("更新库存失败: 商品={}, 仓库={}, 数量={}", productId, warehouseId, quantity, e);
            return "更新库存失败: " + e.getMessage();
        }
    }

    /**
     * 查询订单
     *
     * @param orderId 订单ID
     * @return 订单信息
     */
    @GetMapping("/query/{orderId}")
    public String queryOrder(@PathVariable String orderId) {
        try {
            return orderService.queryOrder(orderId);
        } catch (Exception e) {
            log.error("查询订单失败: {}", orderId, e);
            return "查询订单失败: " + e.getMessage();
        }
    }

    /**
     * 并发处理订单，测试分布式锁
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @GetMapping("/concurrent/{orderId}")
    public String concurrentProcessOrder(@PathVariable String orderId) {
        try {
            // 创建5个并发请求
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return orderService.processOrder(orderId);
                    } catch (Exception e) {
                        log.error("并发处理订单失败: {}", orderId, e);
                        return "失败: " + e.getMessage();
                    }
                }, executor));
            }

            // 等待所有请求完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 收集结果
            StringBuilder result = new StringBuilder("并发处理订单结果:\n");
            for (int i = 0; i < futures.size(); i++) {
                result.append("请求").append(i + 1).append(": ").append(futures.get(i).get()).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("并发处理订单异常: {}", orderId, e);
            return "并发处理订单异常: " + e.getMessage();
        }
    }

    /**
     * 测试重试功能
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @GetMapping("/retry/{orderId}")
    public String processOrderWithRetry(@PathVariable String orderId) {
        try {
            return orderService.processOrderWithRetry(orderId);
        } catch (Exception e) {
            log.error("带重试处理订单失败: {}", orderId, e);
            return "带重试处理订单失败: " + e.getMessage();
        }
    }

    /**
     * 测试并发重试功能
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @GetMapping("/concurrent-retry/{orderId}")
    public String concurrentProcessOrderWithRetry(@PathVariable String orderId) {
        try {
            // 创建5个并发请求
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return orderService.processOrderWithRetry(orderId);
                    } catch (Exception e) {
                        log.error("并发重试处理订单失败: {}", orderId, e);
                        return "失败: " + e.getMessage();
                    }
                }, executor));
            }

            // 等待所有请求完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 收集结果
            StringBuilder result = new StringBuilder("并发重试处理订单结果:\n");
            for (int i = 0; i < futures.size(); i++) {
                result.append("请求").append(i + 1).append(": ").append(futures.get(i).get()).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("并发重试处理订单异常: {}", orderId, e);
            return "并发重试处理订单异常: " + e.getMessage();
        }
    }

    /**
     * 测试可重入锁功能
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @GetMapping("/reentrant/{orderId}")
    public String processOrderWithReentrant(@PathVariable String orderId) {
        try {
            return orderService.processOrderWithReentrant(orderId);
        } catch (Exception e) {
            log.error("可重入锁处理订单失败: {}", orderId, e);
            return "可重入锁处理订单失败: " + e.getMessage();
        }
    }

    /**
     * 测试并发可重入锁功能
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @GetMapping("/concurrent-reentrant/{orderId}")
    public String concurrentProcessOrderWithReentrant(@PathVariable String orderId) {
        try {
            // 创建3个并发请求
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return orderService.processOrderWithReentrant(orderId);
                    } catch (Exception e) {
                        log.error("并发可重入锁处理订单失败: {}", orderId, e);
                        return "失败: " + e.getMessage();
                    }
                }, executor));
            }

            // 等待所有请求完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 收集结果
            StringBuilder result = new StringBuilder("并发可重入锁处理订单结果:\n");
            for (int i = 0; i < futures.size(); i++) {
                result.append("请求").append(i + 1).append(": ").append(futures.get(i).get()).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("并发可重入锁处理订单异常: {}", orderId, e);
            return "并发可重入锁处理订单异常: " + e.getMessage();
        }
    }
}