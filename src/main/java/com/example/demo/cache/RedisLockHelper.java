package com.example.demo.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockHelper {

    private final RedisTemplate<String, Object> redisTemplate;

    public <T> T getOrLoad(
            String cacheKey,
            Supplier<T> cacheGetter,
            Consumer<T> cacheSetter,
            Supplier<T> dbLoader,
            int maxRetries,
            long ttlMs,
            long waitMs,
            long sleepMs) {
        T cached = cacheGetter.get();
        if (cached != null) {
            return cached;
        }

        String lockKey = cacheKey + ":lock";
        try (LockHandle handle = acquireWithRetry(lockKey, maxRetries, ttlMs, waitMs, sleepMs)) {
            if (handle != null) {
                T cachedAgain = cacheGetter.get();
                if (cachedAgain != null) {
                    return cachedAgain;
                }
                T loaded = dbLoader.get();
                if (loaded != null) {
                    cacheSetter.accept(loaded);
                }
                return loaded;
            }
        }
        return dbLoader.get();
    }

    private LockHandle acquireWithRetry(
            String lockKey,
            int maxRetries,
            long ttlMs,
            long waitMs,
            long sleepMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            LockHandle handle = tryAcquire(lockKey, ttlMs, waitMs, sleepMs);
            if (handle != null) {
                return handle;
            }
        }
        return null;
    }

    private LockHandle tryAcquire(
            String lockKey,
            long ttlMs,
            long waitMs,
            long sleepMs) {
        long deadline = System.currentTimeMillis() + waitMs;
        while (true) {
            String token = UUID.randomUUID().toString();
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, Duration.ofMillis(ttlMs));
            if (Boolean.TRUE.equals(acquired)) {
                return new LockHandle(lockKey, token);
            }
            if (System.currentTimeMillis() >= deadline) {
                return null;
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private final class LockHandle implements AutoCloseable {
        private final String lockKey;
        private final String token;
        private boolean closed;

        private LockHandle(String lockKey, String token) {
            this.lockKey = lockKey;
            this.token = token;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                Object current = redisTemplate.opsForValue().get(lockKey);
                if (current instanceof String && token.equals(current)) {
                    redisTemplate.delete(lockKey);
                }
            } catch (Exception ex) {
                log.warn("[RedisLockHelper] failed to release lock key={}", lockKey, ex);
            }
        }
    }
}
