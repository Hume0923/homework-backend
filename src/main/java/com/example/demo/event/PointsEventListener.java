package com.example.demo.event;

import com.example.demo.cache.LeaderboardRedis;
import com.example.demo.cache.RedisCacheLoadGuard;
import com.example.demo.cache.UserTotalPointsRedis;
import com.example.demo.mq.RocketMqProducer;
import com.example.demo.mq.event.UserPointsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointsEventListener {

    private final UserTotalPointsRedis userTotalPointsRedis;
    private final RedisCacheLoadGuard redisCacheLoadGuard;
    private final LeaderboardRedis leaderboardRedis;
    private final RocketMqProducer rocketMqProducer;
    private final ObjectMapper objectMapper;

    @Value("${demo.mq.user-points-topic}")
    private String userPointsTopic;

    @Value("${demo.cache.load-lock-ttl-ms:3000}")
    private long loadLockTtlMs;

    @Value("${demo.cache.load-lock-retry:3}")
    private int loadLockRetry;

    @Value("${demo.cache.load-lock-sleep-ms:50}")
    private long loadLockSleepMs;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPointsChanged(PointsChangedEvent event) {
        deleteTotalWithLock(event.userId());
        try {
            UserPointsEvent payload = new UserPointsEvent();
            payload.setUserId(event.userId());
            payload.setAmount(event.amount());
            payload.setReason(event.reason());
            payload.setPointId(event.pointId());
            payload.setCreatedAt(Instant.now());
            String json = objectMapper.writeValueAsString(payload);
            rocketMqProducer.send(userPointsTopic, json);
        } catch (Exception ex) {
            log.warn("[PointsEventListener][onPointsChanged] error", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to publish event");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPointsDeleted(PointsDeletedEvent event) {
        deleteTotalWithLock(event.userId());
        leaderboardRedis.removeUser(event.userId());
    }

    private void deleteTotalWithLock(String userId) {
        redisCacheLoadGuard.withLock(
                userTotalPointsRedis.getKey(userId),
                loadLockRetry,
                loadLockTtlMs,
                loadLockSleepMs,
                () -> userTotalPointsRedis.deleteTotal(userId));
    }
}
