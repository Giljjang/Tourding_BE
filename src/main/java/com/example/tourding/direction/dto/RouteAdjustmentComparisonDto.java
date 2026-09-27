package com.example.tourding.direction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteAdjustmentComparisonDto {
    private Double durationDiffMinutes;
    private Double distanceDiffKm;
    private Integer difficultyLevelDiff;
}
