package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RouteLocationName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RouteLocationNameRepository extends JpaRepository<RouteLocationName, Long> {
    List<RouteLocationName> findRouteLocationNameBySummaryId(Long summaryId);
    
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RouteLocationName rln WHERE rln.summary.id = :summaryId")
    @Transactional
    void deleteBySummaryId(@Param("summaryId") Long summaryId);
}
