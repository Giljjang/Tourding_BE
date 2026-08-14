package com.example.tourding.ai.repository;

import com.example.tourding.ai.entity.AiRouteRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiRouteRequestRepository extends JpaRepository<AiRouteRequest, Long> {
}
