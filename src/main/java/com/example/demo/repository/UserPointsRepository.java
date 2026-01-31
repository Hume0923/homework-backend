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
            UPDATE user_points
            SET total_points = total_points + :amount,
                version = version + 1
            WHERE user_id = :userId
              AND version = :version
            """, nativeQuery = true)
    int updateAddPointsWithVersion(
            @Param("userId") String userId,
            @Param("amount") long amount,
            @Param("version") long version);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO user_points (user_id, total_points, version)
            VALUES (:userId, :amount, 1)
            """, nativeQuery = true)
    int insertNew(@Param("userId") String userId, @Param("amount") long amount);

    List<UserPoints> findTop20ByOrderByTotalPointsDesc();
}
