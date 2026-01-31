package com.example.demo.repository;

import com.example.demo.model.points.entity.PointRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointRecordRepository extends JpaRepository<PointRecord, Long> {
    List<PointRecord> findByUserId(String userId);
    long deleteByUserId(String userId);
}
