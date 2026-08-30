package com.example.tourding.user.repository;

import com.example.tourding.user.entity.UserRidingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRidingProfileRepository extends JpaRepository<UserRidingProfile, Long> {
    Optional<UserRidingProfile> findByUserId(Long userId);

    @Modifying
    @Query("delete from UserRidingProfile p where p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
