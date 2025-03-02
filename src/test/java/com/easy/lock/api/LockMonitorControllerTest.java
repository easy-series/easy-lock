package com.easy.lock.api;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.easy.lock.AbstractIntegrationTest;
import com.easy.lock.monitor.LockMetrics;

/**
 * 锁监控API测试
 */
@AutoConfigureMockMvc
class LockMonitorControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LockMetrics lockMetrics;

    @Autowired
    private RedisLock redisLock;

    private final String testKey = "test:monitor:key";

    @BeforeEach
    void setUp() {
        lockMetrics.clearStats();
        try {
            redisLock.unlock(testKey);
        } catch (Exception ignored) {
        }
    }

    @Test
    void testGetLockStatsEmpty() throws Exception {
        mockMvc.perform(get("/lock/monitor/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetLockStatsWithData() throws Exception {
        // 生成一些锁统计数据
        redisLock.tryLock(testKey, 1);
        redisLock.unlock(testKey);

        mockMvc.perform(get("/lock/monitor/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$." + testKey).exists())
                .andExpect(jsonPath("$." + testKey + ".acquireCount").value(1))
                .andExpect(jsonPath("$." + testKey + ".failCount").value(0))
                .andExpect(jsonPath("$." + testKey + ".holdTime").isNumber())
                .andExpect(jsonPath("$." + testKey + ".waitTime").isNumber());
    }

    @Test
    void testClearStats() throws Exception {
        // 生成一些锁统计数据
        redisLock.tryLock(testKey, 1);
        redisLock.unlock(testKey);

        // 确认有数据
        mockMvc.perform(get("/lock/monitor/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));

        // 清除数据
        mockMvc.perform(post("/lock/monitor/clear")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 确认数据已清除
        mockMvc.perform(get("/lock/monitor/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}