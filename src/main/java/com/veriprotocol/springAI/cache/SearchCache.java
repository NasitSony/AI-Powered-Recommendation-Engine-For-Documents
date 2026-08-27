package com.veriprotocol.springAI.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SearchCache {

    private final StringRedisTemplate redis;

    public SearchCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void put(
            String key,
            String value,
            Duration ttl) {

        redis.opsForValue().set(
                key,
                value,
                ttl
        );
    }

    public Optional<String> get(String key) {

        String value =
                redis.opsForValue().get(key);

        return Optional.ofNullable(value);
    }

    public void delete(String key) {
        redis.delete(key);
    }

    public void invalidateTenant(String tenantId) {

        String pattern =
                "search:" + tenantId + ":*";

        var keys = redis.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}