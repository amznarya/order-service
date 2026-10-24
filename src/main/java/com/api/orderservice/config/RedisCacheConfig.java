package com.api.orderservice.config;

import com.api.orderservice.dto.OrderResponse;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.Set;

@Configuration
@EnableCaching
public class RedisCacheConfig {


    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(
            JsonMapper jsonMapper
    ) {
        JacksonJsonRedisSerializer<OrderResponse> serializer =
                new JacksonJsonRedisSerializer<>(
                        jsonMapper,
                        OrderResponse.class
                );

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(serializer)
                );
    }

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            RedisCacheConfiguration redisCacheConfiguration
    ) {
        RedisCacheWriter writer = RedisCacheWriter.create(
                connectionFactory,
                RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites
        );

        return RedisCacheManager.builder(writer)
                .cacheDefaults(redisCacheConfiguration)
                .initialCacheNames(Set.of("orders"))
                .enableStatistics()
                .transactionAware()
                .build();
    }
}