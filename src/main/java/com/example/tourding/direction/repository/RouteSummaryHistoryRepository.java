package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RouteSummaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RouteSummaryHistoryRepository extends JpaRepository<RouteSummaryHistory, Long> {
    Optional<RouteSummaryHistory> findFirstByRouteSummaryIdAndUserIdAndRestoredFalseOrderByCreatedAtDesc(Long routeSummaryId, Long userId);
}
