package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RouteGuide;
import com.example.tourding.direction.entity.RoutePath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteGuideRepository extends JpaRepository<RouteGuide, Long> {
    List<RouteGuide> findBySummary_User_id(Long userId);
}
