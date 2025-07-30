package com.example.tourding.direction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;

@Repository

public interface DirectionRepository extends JpaRepository<Path, Long> {
}
