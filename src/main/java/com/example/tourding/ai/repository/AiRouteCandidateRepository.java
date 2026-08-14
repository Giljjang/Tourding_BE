package com.example.tourding.ai.repository;

import com.example.tourding.ai.entity.AiRouteCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiRouteCandidateRepository extends JpaRepository<AiRouteCandidate, Long> {
    List<AiRouteCandidate> findByAiRouteRequestIdOrderByRankNoAsc(Long aiRouteRequestId);
}
