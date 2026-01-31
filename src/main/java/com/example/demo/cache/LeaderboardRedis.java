package com.example.demo.cache;

import com.example.demo.model.points.cache.RedisKeys;
import com.example.demo.model.points.entity.UserPoints;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LeaderboardRedis {

    private static final DefaultRedisScript<Long> REPLACE_TOP_SCRIPT;

    static {
        REPLACE_TOP_SCRIPT = new DefaultRedisScript<>();
        REPLACE_TOP_SCRIPT.setScriptText(
                "redis.call('DEL', KEYS[1]) " +
                "if #ARGV == 0 then return 1 end " +
                "for i=1,#ARGV,2 do " +
                "redis.call('ZADD', KEYS[1], ARGV[i], ARGV[i+1]) " +
                "end " +
                "return 1");
        REPLACE_TOP_SCRIPT.setResultType(Long.class);
    }

    private final RedisTemplate<String, Object> redisTemplate;

    public Set<ZSetOperations.TypedTuple<String>> getTop(int limit) {
        @SuppressWarnings("unchecked")
        Set<ZSetOperations.TypedTuple<String>> entries =
                (Set<ZSetOperations.TypedTuple<String>>) (Set<?>) redisTemplate.opsForZSet()
                        .reverseRangeWithScores(RedisKeys.LEADERBOARD_KEY, 0, limit - 1);
        return entries;
    }

    public void replaceTop(List<UserPoints> topUsers) {
        List<String> keys = List.of(RedisKeys.LEADERBOARD_KEY);
        List<Object> args = new ArrayList<>();
        if (topUsers == null || topUsers.isEmpty()) {
            Long result = redisTemplate.execute(REPLACE_TOP_SCRIPT, keys);
            if (result == null) {
                throw new IllegalStateException("Redis lua script failed for leaderboard replace");
            }
            return;
        }
        for (UserPoints user : topUsers) {
            if (user == null || user.getUserId() == null) {
                continue;
            }
            if (user.getTotalPoints() == null) {
                continue;
            }
            args.add(user.getTotalPoints().doubleValue());
            args.add(user.getUserId());
        }
        Long result = redisTemplate.execute(REPLACE_TOP_SCRIPT, keys, args.toArray());
        if (result == null) {
            throw new IllegalStateException("Redis lua script failed for leaderboard replace");
        }
    }

    public void removeUser(String userId) {
        redisTemplate.opsForZSet().remove(RedisKeys.LEADERBOARD_KEY, userId);
    }
}
