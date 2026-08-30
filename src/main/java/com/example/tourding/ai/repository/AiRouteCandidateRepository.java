package com.example.tourding.ai.repository;

import com.example.tourding.ai.entity.AiRouteCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiRouteCandidateRepository extends JpaRepository<AiRouteCandidate, Long> {
    List<AiRouteCandidate> findByAiRouteRequestIdOrderByRankNoAsc(Long aiRouteRequestId);

    @Modifying
    @Query("delete from AiRouteCandidate c where c.aiRouteRequest.id in :requestIds")
    void deleteAllByAiRouteRequestIdIn(@Param("requestIds") List<Long> requestIds);
}
