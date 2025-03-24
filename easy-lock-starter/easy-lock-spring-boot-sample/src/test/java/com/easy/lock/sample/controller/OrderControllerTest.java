package com.easy.lock.sample.controller;

import com.easy.lock.sample.RedisTestConfiguration;
import com.easy.lock.sample.TestRedisLockConfiguration;
import com.easy.lock.sample.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderController测试类
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ RedisTestConfiguration.class, TestRedisLockConfiguration.class })
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("测试处理单个订单")
    public void testProcessOrder() throws Exception {
        String orderId = "test123";
        String expectedResponse = "订单: " + orderId + " 处理成功";

        when(orderService.processOrder(orderId)).thenReturn(expectedResponse);

        MvcResult result = mockMvc.perform(get("/api/order/process/{orderId}", orderId))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertEquals(expectedResponse, content);
        verify(orderService, times(1)).processOrder(orderId);
    }

    @Test
    @DisplayName("测试更新库存")
    public void testUpdateStock() throws Exception {
        String productId = "product123";
        String warehouseId = "warehouse456";
        int quantity = 10;
        String expectedResponse = "库存更新成功: 商品=" + productId + ", 仓库=" + warehouseId + ", 数量=" + quantity;

        when(orderService.updateStock(productId, warehouseId, quantity)).thenReturn(expectedResponse);

        MvcResult result = mockMvc.perform(get("/api/order/stock/{productId}/{warehouseId}/{quantity}",
                productId, warehouseId, quantity))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertEquals(expectedResponse, content);
        verify(orderService, times(1)).updateStock(productId, warehouseId, quantity);
    }

    @Test
    @DisplayName("测试查询订单")
    public void testQueryOrder() throws Exception {
        String orderId = "test123";
        String expectedResponse = "订单: " + orderId + " 查询结果";

        when(orderService.queryOrder(orderId)).thenReturn(expectedResponse);

        MvcResult result = mockMvc.perform(get("/api/order/query/{orderId}", orderId))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertEquals(expectedResponse, content);
        verify(orderService, times(1)).queryOrder(orderId);
    }

    @Test
    @DisplayName("测试并发处理订单")
    public void testConcurrentProcessOrder() throws Exception {
        String orderId = "test123";
        String expectedResult = "订单: " + orderId + " 处理成功";

        when(orderService.processOrder(orderId)).thenReturn(expectedResult);

        MvcResult result = mockMvc.perform(get("/api/order/concurrent/{orderId}", orderId))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("并发处理订单结果"));
        assertTrue(content.contains(expectedResult));

        // 由于是并发请求，应该被调用5次
        verify(orderService, times(5)).processOrder(orderId);
    }

    @Test
    @DisplayName("测试带重试功能的订单处理")
    public void testProcessOrderWithRetry() throws Exception {
        String orderId = "test123";
        String expectedResponse = "订单: " + orderId + " 处理成功(带重试)";

        when(orderService.processOrderWithRetry(orderId)).thenReturn(expectedResponse);

        MvcResult result = mockMvc.perform(get("/api/order/retry/{orderId}", orderId))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertEquals(expectedResponse, content);
        verify(orderService, times(1)).processOrderWithRetry(orderId);
    }

    @Test
    @DisplayName("测试并发重试功能")
    public void testConcurrentProcessOrderWithRetry() throws Exception {
        String orderId = "test123";
        String expectedResult = "订单: " + orderId + " 处理成功(带重试)";

        when(orderService.processOrderWithRetry(orderId)).thenReturn(expectedResult);

        MvcResult result = mockMvc.perform(get("/api/order/concurrent-retry/{orderId}", orderId))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("并发重试处理订单结果"));
        assertTrue(content.contains(expectedResult));

        // 由于是并发请求，应该被调用5次
        verify(orderService, times(5)).processOrderWithRetry(orderId);
    }

    @Test
    @DisplayName("测试可重入锁功能")
    public void testProcessOrderWithReentrant() throws Exception {
        String orderId = "test123";
        String expectedResponse = "订单: " + orderId + " 外部处理成功, 内部结果: 订单详情处理成功";

        when(orderService.processOrderWithReentrant(orderId)).thenReturn(expectedResponse);

        MvcResult result = mockMvc.perform(get("/api/order/reentrant/{orderId}", orderId))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertEquals(expectedResponse, content);
        verify(orderService, times(1)).processOrderWithReentrant(orderId);
    }

    @Test
    @DisplayName("测试并发可重入锁功能")
    public void testConcurrentProcessOrderWithReentrant() throws Exception {
        String orderId = "test123";
        String expectedResult = "订单: " + orderId + " 外部处理成功, 内部结果: 订单详情处理成功";

        when(orderService.processOrderWithReentrant(orderId)).thenReturn(expectedResult);

        MvcResult result = mockMvc.perform(get("/api/order/concurrent-reentrant/{orderId}", orderId))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("并发可重入锁处理订单结果"));
        assertTrue(content.contains(expectedResult));

        // 由于是并发请求，应该被调用3次
        verify(orderService, times(3)).processOrderWithReentrant(orderId);
    }

    @Test
    @DisplayName("测试处理订单失败场景")
    public void testProcessOrderFailure() throws Exception {
        String orderId = "test123";
        String errorMessage = "锁获取失败";

        when(orderService.processOrder(orderId)).thenThrow(new RuntimeException(errorMessage));

        MvcResult result = mockMvc.perform(get("/api/order/process/{orderId}", orderId))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("处理订单失败"));
        assertTrue(content.contains(errorMessage));
        verify(orderService, times(1)).processOrder(orderId);
    }

    @Test
    @DisplayName("测试真实并发场景")
    public void testRealConcurrency() throws Exception {
        String orderId = "concurrent123";
        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // 每个线程都会请求处理同一个订单
        List<CompletableFuture<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            CompletableFuture<MvcResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return mockMvc.perform(get("/api/order/process/{orderId}", orderId))
                            .andReturn();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有请求完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);

        // 验证所有请求都调用了service
        verify(orderService, times(numThreads)).processOrder(orderId);

        // 关闭线程池
        executor.shutdown();
    }
}