package com.example.demo.mq.event;

import lombok.Data;

import java.time.Instant;

@Data
public class UserPointsEvent {
    private String userId;
    private Long amount;
    private Long total;
    private String reason;
    private Long pointId;
    private Instant createdAt;
}
