package com.example.demo.service;

import com.example.demo.cache.LeaderboardRedis;
import com.example.demo.cache.RedisLockHelper;
import com.example.demo.cache.UserTotalPointsRedis;
import com.example.demo.controller.dto.AddPointsRequest;
import com.example.demo.controller.dto.LeaderboardEntry;
import com.example.demo.controller.dto.PointResponse;
import com.example.demo.controller.dto.UpdateReasonResponse;
import com.example.demo.controller.dto.TotalPointsResponse;
import com.example.demo.controller.dto.UpdateReasonRequest;
import com.example.demo.model.points.cache.CachedTotalPoints;
import com.example.demo.model.points.entity.PointRecord;
import com.example.demo.model.points.entity.UserPoints;
import com.example.demo.event.PointsChangedEvent;
import com.example.demo.event.PointsDeletedEvent;
import com.example.demo.repository.PointRecordRepository;
import com.example.demo.repository.UserPointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsService {

    private final PointRecordRepository pointRecordRepository;
    private final UserPointsRepository userPointsRepository;
    private final UserTotalPointsRedis userTotalPointsRedis;
    private final LeaderboardRedis leaderboardRedis;
    private final RedisLockHelper redisLockHelper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${demo.cache.load-lock-ttl-ms:3000}")
    private long loadLockTtlMs;

    @Value("${demo.cache.load-lock-retry:3}")
    private int loadLockRetry;

    @Value("${demo.cache.load-lock-wait-ms:500}")
    private long loadLockWaitMs;

    @Value("${demo.cache.load-lock-sleep-ms:50}")
    private long loadLockSleepMs;

    @Transactional
    public PointResponse addPoints(AddPointsRequest request) {
        validateAddRequest(request);

        PointRecord record = new PointRecord();
        record.setUserId(request.getUserId());
        record.setAmount(request.getAmount());
        record.setReason(request.getReason());
        PointRecord saved = pointRecordRepository.save(record);

        CachedTotalPoints updated = updateUserPointsWithVersion(
                request.getUserId(),
                request.getAmount());
        long total = updated.total();

        eventPublisher.publishEvent(new PointsChangedEvent(
                saved.getUserId(),
                saved.getId(),
                saved.getAmount(),
                total,
                saved.getReason()));

        PointResponse response = new PointResponse();
        response.setId(saved.getId());
        response.setUserId(saved.getUserId());
        response.setAmount(saved.getAmount());
        response.setReason(saved.getReason());
        response.setTotal(total);
        response.setCreatedAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now());
        return response;
    }

    public TotalPointsResponse getTotalPoints(String userId) {
        CachedTotalPoints cached = userTotalPointsRedis.getCached(userId);
        if (cached != null) {
            TotalPointsResponse response = new TotalPointsResponse();
            response.setUserId(userId);
            response.setTotal(cached.total());
            return response;
        }

        log.info("[PointsService][getTotalPoints] userId:{} no cache", userId);
        CachedTotalPoints loaded = redisLockHelper.getOrLoad(
                userTotalPointsRedis.getKey(userId),
                () -> userTotalPointsRedis.getCached(userId),
                cachedValue -> userTotalPointsRedis.setIfNewer(userId, cachedValue),
                () -> loadFromDb(userId),
                loadLockRetry,
                loadLockTtlMs,
                loadLockWaitMs,
                loadLockSleepMs);
        long total = loaded == null ? 0L : loaded.total();

        TotalPointsResponse response = new TotalPointsResponse();
        response.setUserId(userId);
        response.setTotal(total);
        return response;
    }

    public List<LeaderboardEntry> getLeaderboard() {
        List<LeaderboardEntry> results = new ArrayList<>();
        var entries = leaderboardRedis.getTop(10);
        if (entries == null) {
            return results;
        }
        for (var entry : entries) {
            if (entry == null || entry.getValue() == null) {
                continue;
            }
            LeaderboardEntry item = new LeaderboardEntry();
            item.setUserId(entry.getValue());
            double score = entry.getScore() == null ? 0.0 : entry.getScore();
            item.setTotal((long) score);
            results.add(item);
        }
        return results;
    }

    @Transactional
    public UpdateReasonResponse updateReason(Long id, UpdateReasonRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required");
        }
        PointRecord record = pointRecordRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "point record not found"));
        record.setReason(request.getReason());
        PointRecord saved = pointRecordRepository.save(record);

        UpdateReasonResponse response = new UpdateReasonResponse();
        response.setId(saved.getId());
        response.setUserId(saved.getUserId());
        response.setAmount(saved.getAmount());
        response.setReason(saved.getReason());
        response.setCreatedAt(saved.getCreatedAt());
        return response;
    }

    @Transactional
    public void deleteUserPoints(String userId) {
        pointRecordRepository.deleteByUserId(userId);
        userPointsRepository.deleteById(userId);
        eventPublisher.publishEvent(new PointsDeletedEvent(userId));
    }

    private void validateAddRequest(AddPointsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        if (request.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required");
        }
    }

    private CachedTotalPoints loadFromDb(String userId) {
        Optional<UserPoints> userPoints = userPointsRepository.findById(userId);
        if (userPoints.isEmpty()) {
            return new CachedTotalPoints(0L, 0L);
        }
        UserPoints data = userPoints.get();
        return new CachedTotalPoints(data.getTotalPoints(), data.getVersion());
    }

    private CachedTotalPoints updateUserPointsWithVersion(String userId, long amount) {
        for (int attempt = 1; attempt <= loadLockRetry; attempt++) {
            Optional<UserPoints> existing = userPointsRepository.findById(userId);
            if (existing.isEmpty()) {
                int inserted = userPointsRepository.insertNew(userId, amount);
                if (inserted > 0) {
                    return new CachedTotalPoints(amount, 1L);
                }
                continue;
            }
            UserPoints current = existing.get();
            long currentVersion = current.getVersion() == null ? 0L : current.getVersion();
            int updated = userPointsRepository.updateAddPointsWithVersion(userId, amount, currentVersion);
            if (updated > 0) {
                long newTotal = (current.getTotalPoints() == null ? 0L : current.getTotalPoints()) + amount;
                return new CachedTotalPoints(newTotal, currentVersion + 1);
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "failed to update points due to version conflict");
    }

}
