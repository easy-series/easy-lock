package com.easy.lock.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 锁监控指标测试
 */
@SpringBootTest
@ActiveProfiles("test")
class LockMetricsTest {

    @Autowired
    private LockMetrics lockMetrics;

    private final String testKey = "test:metrics:key";

    @BeforeEach
    void setUp() {
        lockMetrics.clearStats();
    }

    @Test
    void testRecordLockAcquire() {
        lockMetrics.recordLockAcquire(testKey);

        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        assertNotNull(stats, "统计信息不应为空");
        assertTrue(stats.containsKey(testKey), "应该包含测试key的统计信息");
        assertEquals(1, stats.get(testKey).getAcquireCount(), "获取次数应该为1");
    }

    @Test
    void testRecordLockFail() {
        lockMetrics.recordLockFail(testKey);

        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        assertNotNull(stats, "统计信息不应为空");
        assertTrue(stats.containsKey(testKey), "应该包含测试key的统计信息");
        assertEquals(1, stats.get(testKey).getFailCount(), "失败次数应该为1");
    }

    @Test
    void testRecordLockHoldTime() {
        long holdTime = 1000L;
        lockMetrics.recordLockHoldTime(testKey, holdTime);

        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        assertNotNull(stats, "统计信息不应为空");
        assertTrue(stats.containsKey(testKey), "应该包含测试key的统计信息");
        assertEquals(holdTime, stats.get(testKey).getHoldTime(), "持有时间应该匹配");
    }

    @Test
    void testRecordLockWaitTime() {
        long waitTime = 500L;
        lockMetrics.recordLockWaitTime(testKey, waitTime);

        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        assertNotNull(stats, "统计信息不应为空");
        assertTrue(stats.containsKey(testKey), "应该包含测试key的统计信息");
        assertEquals(waitTime, stats.get(testKey).getWaitTime(), "等待时间应该匹配");
    }

    @Test
    void testClearStats() {
        lockMetrics.recordLockAcquire(testKey);
        lockMetrics.recordLockFail(testKey);

        Map<String, LockMetrics.LockStat> statsBefore = lockMetrics.getLockStats();
        assertFalse(statsBefore.isEmpty(), "清除前统计信息不应为空");

        lockMetrics.clearStats();

        Map<String, LockMetrics.LockStat> statsAfter = lockMetrics.getLockStats();
        assertTrue(statsAfter.isEmpty(), "清除后统计信息应为空");
    }

    @Test
    void testLockStatCalculations() {
        // 记录一些数据
        lockMetrics.recordLockAcquire(testKey);
        lockMetrics.recordLockAcquire(testKey);
        lockMetrics.recordLockFail(testKey);
        lockMetrics.recordLockHoldTime(testKey, 1000L);
        lockMetrics.recordLockHoldTime(testKey, 2000L);
        lockMetrics.recordLockWaitTime(testKey, 300L);
        lockMetrics.recordLockWaitTime(testKey, 400L);
        lockMetrics.recordLockWaitTime(testKey, 500L);

        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        LockMetrics.LockStat stat = stats.get(testKey);

        assertEquals(2, stat.getAcquireCount(), "获取次数应该为2");
        assertEquals(1, stat.getFailCount(), "失败次数应该为1");
        assertEquals(3000L, stat.getHoldTime(), "总持有时间应该为3000ms");
        assertEquals(1200L, stat.getWaitTime(), "总等待时间应该为1200ms");

        // 测试计算值
        assertEquals(2.0 / 3.0, stat.getSuccessRate(), 0.001, "成功率应该为2/3");
        assertEquals(1500.0, stat.getAvgHoldTime(), 0.001, "平均持有时间应该为1500ms");
        assertEquals(400.0, stat.getAvgWaitTime(), 0.001, "平均等待时间应该为400ms");
    }

    @Test
    void testLockStatEdgeCases() {
        LockMetrics.LockStat emptyStat = new LockMetrics.LockStat();

        assertEquals(0, emptyStat.getSuccessRate(), "空统计的成功率应该为0");
        assertEquals(0, emptyStat.getAvgHoldTime(), "空统计的平均持有时间应该为0");
        assertEquals(0, emptyStat.getAvgWaitTime(), "空统计的平均等待时间应该为0");

        // 只有失败，没有成功
        lockMetrics.recordLockFail(testKey);
        lockMetrics.recordLockWaitTime(testKey, 100L);

        Map<String, LockMetrics.LockStat> stats = lockMetrics.getLockStats();
        LockMetrics.LockStat failOnlyStat = stats.get(testKey);

        assertEquals(0, failOnlyStat.getSuccessRate(), "只有失败的成功率应该为0");
        assertEquals(0, failOnlyStat.getAvgHoldTime(), "只有失败的平均持有时间应该为0");
        assertEquals(100.0, failOnlyStat.getAvgWaitTime(), "只有失败的平均等待时间应该为100ms");
    }
}