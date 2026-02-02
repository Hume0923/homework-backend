package com.example.demo.cache;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisCacheLoadGuard {

    private final RedisTemplate<String, Object> redisTemplate;

    public <T> T getOrLoad(
            String cacheKey,
            Supplier<T> cacheGetter,
            Consumer<T> cacheSetter,
            Supplier<T> dbLoader,
            int maxRetries,
            long ttlMs,
            long sleepMs) {
        String lockKey = cacheKey + ":lock";
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            T cached = cacheGetter.get();
            if (cached != null) {
                return cached;
            }
            try (LockHandle handle = tryAcquire(lockKey, ttlMs)) {
                if (handle != null) {
                    T loaded = dbLoader.get();
                    if (loaded != null) {
                        cacheSetter.accept(loaded);
                    }
                    return loaded;
                }
            }
            sleepQuietly(sleepMs);
        }
        throw new IllegalStateException("cache not available after retries for key: " + cacheKey);
    }

    public void withLock(
            String cacheKey,
            int maxRetries,
            long ttlMs,
            long sleepMs,
            Runnable action) {
        String lockKey = cacheKey + ":lock";
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (LockHandle handle = tryAcquire(lockKey, ttlMs)) {
                if (handle != null) {
                    action.run();
                    return;
                }
            }
            sleepQuietly(sleepMs);
        }
        throw new IllegalStateException("lock not available after retries for key: " + cacheKey);
    }

    private void sleepQuietly(long sleepMs) {
        if (sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private LockHandle tryAcquire(String lockKey, long ttlMs) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, Duration.ofMillis(ttlMs));
        if (Boolean.TRUE.equals(acquired)) {
            return new LockHandle(lockKey, token);
        }
        return null;
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
            Object current = redisTemplate.opsForValue().get(lockKey);
            if (current instanceof String && token.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        }
    }
}
