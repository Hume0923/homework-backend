package com.example.demo.controller;

import com.example.demo.controller.dto.AddPointsRequest;
import com.example.demo.controller.dto.LeaderboardEntry;
import com.example.demo.controller.dto.PointResponse;
import com.example.demo.controller.dto.TotalPointsResponse;
import com.example.demo.controller.dto.UpdateReasonRequest;
import com.example.demo.controller.dto.UpdateReasonResponse;
import com.example.demo.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @PostMapping
    public ResponseEntity<PointResponse> addPoints(@RequestBody AddPointsRequest request) {
        PointResponse response = pointsService.addPoints(request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{userId}")
    public TotalPointsResponse getTotalPoints(@PathVariable String userId) {
        return pointsService.getTotalPoints(userId);
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> getLeaderboard() {
        return pointsService.getLeaderboard();
    }

    @PutMapping("/{id}")
    public UpdateReasonResponse updateReason(@PathVariable Long id, @RequestBody UpdateReasonRequest request) {
        return pointsService.updateReason(id, request);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUserPoints(@PathVariable String userId) {
        pointsService.deleteUserPoints(userId);
        return ResponseEntity.noContent().build();
    }
}
