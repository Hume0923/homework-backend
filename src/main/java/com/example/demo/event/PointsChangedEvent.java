package com.example.demo.event;

public record PointsChangedEvent(
        String userId,
        Long pointId,
        Long amount,
        Long total,
        String reason) {
}
