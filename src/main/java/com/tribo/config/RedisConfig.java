package com.tribo.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Configuração do cache Redis com serialização JSON.
 *
 * Caches configurados:
 * - subscription-check: 10 min — verificação de assinatura ativa (hot path crítico)
 * - published-courses:   5 min — listagem paginada de cursos publicados
 *
 * IMPORTANTE: invalidação automática via @CacheEvict nos métodos de escrita.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheConfiguration defaultCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration subCheckConfig = defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10));  // assinatura muda raramente

        RedisCacheConfiguration coursesConfig = defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5));   // cursos mudam pouco

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig())
                .withInitialCacheConfigurations(Map.of(
                        "subscription-check", subCheckConfig,
                        "published-courses",  coursesConfig
                ))
                .build();
    }
}
