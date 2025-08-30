package com.example.tourding.direction.repository;

import com.example.tourding.direction.entity.RouteSummary;
import com.example.tourding.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository

public interface RouteSummaryRepository extends JpaRepository<RouteSummary, Long> {
    Optional<RouteSummary> findRouteSummaryByUserId(Long userId);
}
