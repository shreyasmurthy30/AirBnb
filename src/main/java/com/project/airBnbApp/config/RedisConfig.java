package com.project.airBnbApp.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.airBnbApp.dto.HotelInfoDto;
import com.project.airBnbApp.dto.HotelSearchCacheData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JsonRedisSerializer<HotelInfoDto> hotelInfoSerializer =
                new JsonRedisSerializer<>(objectMapper, HotelInfoDto.class);

        JsonRedisSerializer<HotelSearchCacheData> searchSerializer =
                new JsonRedisSerializer<>(objectMapper, HotelSearchCacheData.class);

        RedisCacheConfiguration hotelInfoConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(hotelInfoSerializer));

        RedisCacheConfiguration searchConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(searchSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration("hotels-info", hotelInfoConfig)
                .withCacheConfiguration("hotels-search", searchConfig)
                .build();
    }
}
