package com.example.demo.controller.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class PointResponse {
    private Long id;
    private String userId;
    private Long amount;
    private String reason;
    private Long total;
    private Instant createdAt;
}
