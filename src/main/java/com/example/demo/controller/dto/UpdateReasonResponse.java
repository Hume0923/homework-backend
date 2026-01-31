package com.example.demo.controller.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class UpdateReasonResponse {
    private Long id;
    private String userId;
    private Long amount;
    private String reason;
    private Instant createdAt;
}
