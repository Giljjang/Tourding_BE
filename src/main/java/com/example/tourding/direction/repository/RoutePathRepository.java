package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RoutePath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RoutePathRepository extends JpaRepository<RoutePath, Long> {
    List<RoutePath> findRoutePathBySummaryId(Long summaryId);
    
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RoutePath rp WHERE rp.summary.id = :summaryId")
    @Transactional
    void deleteBySummaryId(@Param("summaryId") Long summaryId);
}
