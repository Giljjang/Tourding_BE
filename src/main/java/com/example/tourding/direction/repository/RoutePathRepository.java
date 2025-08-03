package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RoutePath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutePathRepository extends JpaRepository<RoutePath, Long> {
    List<RoutePath> findBySummary_User_id(Long userId);
}
