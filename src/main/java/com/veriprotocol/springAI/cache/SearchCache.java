package com.veriprotocol.springAI.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SearchCache {

    private static final Duration COOLDOWN =
            Duration.ofSeconds(5);

    private final StringRedisTemplate redis;

    /*
     * 0 = circuit closed
     *
     * Otherwise this stores the System.nanoTime() timestamp
     * until which Redis should be bypassed.
     */
    private final AtomicLong openUntilNanos =
            new AtomicLong(0);

    public SearchCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isCircuitOpen() {

        long until = openUntilNanos.get();

        if (until == 0) {
            return false;
        }

        if (System.nanoTime() >= until) {

            // cooldown expired; allow a Redis probe again
            openUntilNanos.compareAndSet(
                    until,
                    0
            );

            return false;
        }

        return true;
    }

    private void openCircuit() {

        long until =
                System.nanoTime()
                        + COOLDOWN.toNanos();

        openUntilNanos.set(until);
    }

    private void closeCircuit() {
        openUntilNanos.set(0);
    }

    public Optional<String> get(String key) {

        if (isCircuitOpen()) {
            return Optional.empty();
        }

        try {

            String value =
                    redis.opsForValue().get(key);

            // Redis responded successfully,
            // even if the key itself was absent.
            closeCircuit();

            return Optional.ofNullable(value);

        } catch (RuntimeException e) {

            openCircuit();
            throw e;
        }
    }

    public void put(
            String key,
            String value,
            Duration ttl) {

        if (isCircuitOpen()) {
            return;
        }

        try {

            redis.opsForValue().set(
                    key,
                    value,
                    ttl
            );

            closeCircuit();

        } catch (RuntimeException e) {

            openCircuit();
            throw e;
        }
    }

    public void delete(String key) {

        if (isCircuitOpen()) {
            return;
        }

        try {

            redis.delete(key);
            closeCircuit();

        } catch (RuntimeException e) {

            openCircuit();
            throw e;
        }
    }

    public void invalidateTenant(String tenantId) {

        if (isCircuitOpen()) {
            return;
        }

        try {

            String pattern =
                    "search:" + tenantId + ":*";

            var keys = redis.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }

            closeCircuit();

        } catch (RuntimeException e) {

            openCircuit();
            throw e;
        }
    }
}