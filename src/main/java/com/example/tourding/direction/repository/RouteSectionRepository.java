package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RoutePath;
import com.example.tourding.direction.entity.RouteSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteSectionRepository extends JpaRepository<RouteSection, Long> {
    List<RouteSection> findBySummary_User_id(Long userId);
}
