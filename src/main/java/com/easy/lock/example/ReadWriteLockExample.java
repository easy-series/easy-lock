package com.easy.lock.example;

import org.springframework.stereotype.Service;

import com.easy.lock.annotation.Lock;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReadWriteLockExample {

    /**
     * 读取数据（使用读锁）
     * 
     * @param resourceId 资源ID
     * @return 资源数据
     */
    @Lock(prefix = "resource", key = "#resourceId", mode = Lock.LockMode.READ)
    public String readResource(String resourceId) {
        log.info("开始读取资源: {}", resourceId);
        try {
            // 模拟读取操作
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("读取资源完成: {}", resourceId);
        return "Resource data: " + resourceId;
    }

    /**
     * 更新数据（使用写锁）
     * 
     * @param resourceId 资源ID
     * @param data       更新的数据
     */
    @Lock(prefix = "resource", key = "#resourceId", mode = Lock.LockMode.WRITE)
    public void updateResource(String resourceId, String data) {
        log.info("开始更新资源: {}, 数据: {}", resourceId, data);
        try {
            // 模拟更新操作
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("更新资源完成: {}", resourceId);
    }
}