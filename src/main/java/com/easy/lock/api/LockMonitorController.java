package com.easy.lock.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easy.lock.monitor.LockMetrics;

import lombok.RequiredArgsConstructor;

/**
 * 锁监控API接口
 */
@RestController
@RequestMapping("/lock/monitor")
@RequiredArgsConstructor
public class LockMonitorController {

    private final LockMetrics lockMetrics;

    /**
     * 获取锁的统计信息
     * 
     * @return 锁的统计信息
     */
    @GetMapping("/stats")
    public Map<String, LockMetrics.LockStat> getLockStats() {
        return lockMetrics.getLockStats();
    }

    /**
     * 清除锁的统计信息
     */
    @PostMapping("/clear")
    public void clearStats() {
        lockMetrics.clearStats();
    }
}