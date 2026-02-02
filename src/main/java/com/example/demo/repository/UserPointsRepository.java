package com.example.demo.repository;

import com.example.demo.model.points.entity.UserPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPointsRepository extends JpaRepository<UserPoints, String> {

    @Modifying
    @Query(value = """
            INSERT INTO user_points (user_id, total_points)
            VALUES (:userId, :amount)
            ON DUPLICATE KEY UPDATE
              total_points = total_points + VALUES(total_points)
            """, nativeQuery = true)
    void upsertAddPoints(
            @Param("userId") String userId,
            @Param("amount") long amount);

    List<UserPoints> findTop20ByOrderByTotalPointsDesc();
}
