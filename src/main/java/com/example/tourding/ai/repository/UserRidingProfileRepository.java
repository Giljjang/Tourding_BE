package com.example.tourding.ai.repository;

import com.example.tourding.ai.entity.UserRidingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRidingProfileRepository extends JpaRepository<UserRidingProfile, Long> {
    Optional<UserRidingProfile> findByUserId(Long userId);
}
