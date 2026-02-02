package com.example.demo.cache;

import com.example.demo.model.points.cache.CachedTotalPoints;
import com.example.demo.model.points.cache.RedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;

@Component
@RequiredArgsConstructor
public class UserTotalPointsRedis {

    @Value("${demo.cache.points-ttl-seconds:600}")
    private long ttlSeconds;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public String getKey(String userId) {
        return RedisKeys.USER_TOTAL_POINTS + userId;
    }

    public CachedTotalPoints getCached(String userId) {
        Object value = redisTemplate.opsForValue().get(getKey(userId));
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            try {
                return objectMapper.readValue((String) value, CachedTotalPoints.class);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Failed to parse cached total points JSON", ex);
            }
        }
        if (value instanceof CachedTotalPoints) {
            return (CachedTotalPoints) value;
        }
        if (value instanceof LinkedHashMap<?, ?>) {
            return objectMapper.convertValue(value, CachedTotalPoints.class);
        }
        throw new IllegalStateException("Unexpected value type in Redis: " + value.getClass().getName());
    }

    public void set(String userId, CachedTotalPoints cached) {
        if (cached == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(cached);
            redisTemplate.opsForValue().set(getKey(userId), payload, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize cached total points JSON", ex);
        }
    }

    public void deleteTotal(String userId) {
        redisTemplate.delete(getKey(userId));
    }
}
