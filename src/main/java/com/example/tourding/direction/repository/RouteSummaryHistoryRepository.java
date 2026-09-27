package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RouteSummaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RouteSummaryHistoryRepository extends JpaRepository<RouteSummaryHistory, Long> {
    Optional<RouteSummaryHistory> findFirstByRouteSummaryIdAndUserIdAndRestoredFalseOrderByCreatedAtDesc(Long routeSummaryId, Long userId);

    @Modifying
    @Query("delete from RouteSummaryHistory h where h.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
