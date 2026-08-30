package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RouteSummary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository

public interface RouteSummaryRepository extends JpaRepository<RouteSummary, Long> {
    Optional<RouteSummary> findRouteSummaryByUserIdAndIsUsed(Long userId, boolean isUsed);
    Optional<RouteSummary> findRouteSummaryByUserId(Long userId);

    @Modifying
    @Query("delete from RouteSummary r where r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
