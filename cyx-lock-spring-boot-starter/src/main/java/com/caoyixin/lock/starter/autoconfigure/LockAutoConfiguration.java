package com.caoyixin.lock.starter.autoconfigure;

import com.caoyixin.lock.core.LockExecutor;
import com.caoyixin.lock.core.LockTemplate;
import com.caoyixin.lock.metrics.LockMetrics;
import com.caoyixin.lock.metrics.MicrometerLockMetrics;
import com.caoyixin.lock.redisson.executor.RedissonLockExecutor;
import com.caoyixin.lock.starter.aspect.LockAspect;
import com.caoyixin.lock.starter.properties.LockProperties;
import com.caoyixin.lock.support.DefaultLockFailureStrategy;
import com.caoyixin.lock.support.DefaultLockKeyBuilder;
import com.caoyixin.lock.support.LockFailureStrategy;
import com.caoyixin.lock.support.LockKeyBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 锁自动配置类，自动装配锁相关的Bean
 *
 * @author caoyixin
 */
@Configuration
@EnableAspectJAutoProxy // 启用AspectJ支持，确保切面生效
@ConditionalOnClass({ RedissonClient.class, LockTemplate.class })
@EnableConfigurationProperties(LockProperties.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class LockAutoConfiguration {

    /**
     * 配置锁执行器
     *
     * @param redissonClient Redisson客户端
     * @return 锁执行器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedissonClient.class)
    public LockExecutor lockExecutor(RedissonClient redissonClient) {
        RedissonLockExecutor executor = new RedissonLockExecutor();
        executor.setRedissonClient(redissonClient);
        return executor;
    }

    /**
     * 配置锁模板
     *
     * @param lockExecutor 锁执行器
     * @param lockMetrics  锁监控指标，可选
     * @return 锁模板
     */
    @Bean
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate(LockExecutor lockExecutor,
            @org.springframework.beans.factory.annotation.Autowired(required = false) LockMetrics lockMetrics) {
        LockTemplate template = new LockTemplate();
        template.setLockExecutor(lockExecutor);
        if (lockMetrics != null) {
            template.setLockMetrics(lockMetrics);
        }
        return template;
    }

    /**
     * 配置锁键生成器
     *
     * @param properties 锁配置属性
     * @return 锁键生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public LockKeyBuilder lockKeyBuilder(LockProperties properties) {
        DefaultLockKeyBuilder keyBuilder = new DefaultLockKeyBuilder();
        keyBuilder.setLockKeyPrefix(properties.getLockKeyPrefix());
        return keyBuilder;
    }

    /**
     * 配置锁失败策略
     *
     * @return 锁失败策略
     */
    @Bean
    @ConditionalOnMissingBean
    public LockFailureStrategy lockFailureStrategy() {
        return new DefaultLockFailureStrategy();
    }

    /**
     * 配置锁切面
     *
     * @param lockTemplate    锁模板
     * @param keyBuilder      锁键生成器
     * @param failureStrategy 锁失败策略
     * @return 锁切面
     */
    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(LockTemplate lockTemplate,
            LockKeyBuilder keyBuilder,
            LockFailureStrategy failureStrategy) {
        LockAspect aspect = new LockAspect();
        aspect.setLockTemplate(lockTemplate);
        aspect.setKeyBuilder(keyBuilder);
        aspect.setFailureStrategy(failureStrategy);
        return aspect;
    }

    /**
     * 配置锁监控指标
     *
     * @param meterRegistry 指标注册器
     * @param properties    锁配置属性
     * @return 锁监控指标
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "cyx-lock", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
    public LockMetrics lockMetrics(MeterRegistry meterRegistry, LockProperties properties) {
        return new MicrometerLockMetrics(meterRegistry);
    }
}