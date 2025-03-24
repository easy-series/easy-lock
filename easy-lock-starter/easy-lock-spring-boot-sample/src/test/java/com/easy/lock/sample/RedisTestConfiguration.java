package com.easy.lock.sample;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Redis测试配置类
 * 注意：测试时需要确保本地Redis服务可用
 */
@Slf4j
@TestConfiguration
public class RedisTestConfiguration {

    @PostConstruct
    public void init() {
        log.info("初始化Redis测试配置");
    }

    @PreDestroy
    public void cleanup() {
        log.info("清理Redis测试配置");
    }

    @Bean
    @ConditionalOnMissingBean
    public JedisConnectionFactory redisConnectionFactory(RedisProperties redisProperties) {
        log.info("创建Redis连接工厂: {}:{}", redisProperties.getHost(), redisProperties.getPort());

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());
        config.setPassword(redisProperties.getPassword());
        config.setDatabase(redisProperties.getDatabase());

        return new JedisConnectionFactory(config);
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(JedisConnectionFactory connectionFactory) {
        log.info("创建RedisTemplate");

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();

        return template;
    }
}