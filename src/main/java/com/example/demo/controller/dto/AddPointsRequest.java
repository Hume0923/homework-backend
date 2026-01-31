package com.example.demo.controller.dto;

import lombok.Data;

@Data
public class AddPointsRequest {
    private String userId;
    private Long amount;
    private String reason;
}
