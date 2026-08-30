package com.example.tourding.ai.repository;

import com.example.tourding.ai.entity.AiRouteRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiRouteRequestRepository extends JpaRepository<AiRouteRequest, Long> {
    List<AiRouteRequest> findByUserId(Long userId);

    @Modifying
    @Query("delete from AiRouteRequest r where r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
