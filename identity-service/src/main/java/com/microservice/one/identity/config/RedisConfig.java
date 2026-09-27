package com.microservice.one.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.data.redis.connection.RedisConnectionFactory;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(

            RedisConnectionFactory redisConnectionFactory,

            ObjectMapper objectMapper) {

        RedisTemplate<String, Object> redisTemplate =
                new RedisTemplate<>();

        redisTemplate.setConnectionFactory(
                redisConnectionFactory);

        /*
         * Serializer for Redis keys.
         */
        StringRedisSerializer stringRedisSerializer =
                new StringRedisSerializer();

        /*
         * Serializer for Redis values.
         */
        GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer =
                new GenericJackson2JsonRedisSerializer();

        /*
         * Keys
         */
        redisTemplate.setKeySerializer(
                stringRedisSerializer);

        redisTemplate.setHashKeySerializer(
                stringRedisSerializer);

        /*
         * Values
         */
        redisTemplate.setValueSerializer(
        		genericJackson2JsonRedisSerializer);

        redisTemplate.setHashValueSerializer(
        		genericJackson2JsonRedisSerializer);

        /*
         * Default serializer
         */
        redisTemplate.setDefaultSerializer(
        		genericJackson2JsonRedisSerializer);

        redisTemplate.afterPropertiesSet();

        return redisTemplate;

    }

}