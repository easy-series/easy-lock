package com.easy.lock.autoconfigure;

import com.easy.lock.aspect.LockAspect;
import com.easy.lock.core.LockExecutor;
import com.easy.lock.core.LockManager;
import com.easy.lock.executor.JdbcLockExecutor;
import com.easy.lock.executor.RedisLockExecutor;
import com.easy.lock.monitor.LockMetrics;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 分布式锁自动配置
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(EasyLockProperties.class)
@ConditionalOnProperty(prefix = "easy.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EasyLockAutoConfiguration {

    /**
     * Redis锁执行器
     */
    @Bean
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(name = "redisLockExecutor")
    public RedisLockExecutor redisLockExecutor(RedissonClient redissonClient) {
        log.info("初始化Redis锁执行器");
        try {
            return new RedisLockExecutor(redissonClient);
        } catch (Exception e) {
            log.error("初始化Redis锁执行器失败", e);
            return null;
        }
    }

    /**
     * JDBC锁执行器
     */
    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(name = "jdbcLockExecutor")
    public JdbcLockExecutor jdbcLockExecutor(DataSource dataSource, EasyLockProperties lockProperties) {
        log.info("初始化JDBC锁执行器");
        return new JdbcLockExecutor(dataSource, lockProperties);
    }

    /**
     * 锁管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public LockManager lockManager(ObjectProvider<List<LockExecutor>> lockExecutorsProvider,
            EasyLockProperties lockProperties) {
        List<LockExecutor> lockExecutors = lockExecutorsProvider.getIfAvailable(ArrayList::new);
        log.info("初始化锁管理器，已加载锁执行器数量: {}", lockExecutors.size());
        return new LockManager(lockExecutors, lockProperties.getLockType());
    }

    /**
     * 锁指标
     */
    @Bean
    @ConditionalOnMissingBean
    public LockMetrics lockMetrics() {
        log.info("初始化锁监控指标");
        return new LockMetrics();
    }

    /**
     * 锁切面
     */
    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(LockManager lockManager, LockMetrics lockMetrics, EasyLockProperties lockProperties) {
        log.info("初始化锁切面");
        return new LockAspect(lockManager, lockMetrics, lockProperties);
    }
}