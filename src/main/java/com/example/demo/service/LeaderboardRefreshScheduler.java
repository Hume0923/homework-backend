package com.example.demo.service;

import com.example.demo.cache.LeaderboardRedis;
import com.example.demo.model.points.entity.UserPoints;
import com.example.demo.repository.UserPointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderboardRefreshScheduler {

    private final UserPointsRepository userPointsRepository;
    private final LeaderboardRedis leaderboardRedis;

    @Scheduled(fixedDelayString = "${demo.leaderboard.refresh-ms:300000}")
    public void refreshLeaderboard() {
        try {
            List<UserPoints> topUsers = userPointsRepository.findTop20ByOrderByTotalPointsDesc();
            leaderboardRedis.replaceTop(topUsers);
            log.debug("[LeaderboardRefreshScheduler] refreshed leaderboard entries={}",
                    topUsers == null ? 0 : topUsers.size());
        } catch (Exception ex) {
            log.error("[LeaderboardRefreshScheduler] refresh failed", ex);
        }
    }
}
