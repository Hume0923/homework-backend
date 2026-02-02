package com.example.demo.model.points.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "user_points")
public class UserPoints {

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "total_points", nullable = false)
    private Long totalPoints;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
