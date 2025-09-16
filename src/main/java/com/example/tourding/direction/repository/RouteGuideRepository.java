package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RouteGuide;
import com.example.tourding.direction.entity.RoutePath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RouteGuideRepository extends JpaRepository<RouteGuide, Long> {
    List<RouteGuide> findRouteGuideBySummaryIdOrderBySequenceNumAsc(Long summaryId);
    
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RouteGuide rg WHERE rg.summary.id = :summaryId")
    @Transactional
    void deleteBySummaryId(@Param("summaryId") Long summaryId);
}
