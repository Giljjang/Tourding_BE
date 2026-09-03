package com.example.tourding.direction.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RouteGuideRespDto {
    private Long routeSummaryId;
    private Boolean isUsed;
    private Double duration;
    private Double distance;
    private Double ascent;
    private Double descent;
    private String uphillLevel;
    private Integer difficultyLevel;
    private RouteAdjustmentComparisonDto adjustmentComparison;
    private List<RouteSurfaceSummaryDto> surfaceSummary;
    private Boolean hasConstruction;
    private Boolean hasSteps;
    private Boolean hasIce;
    private Double preferenceScore;
    private RouteOptionDto appliedOption;
    private List<RouteGuideStepDto> guides;
    private List<RoutePathRespDto> paths;
    private List<RouteLocationNameRespDto> locations;
    private Map<String, Object> extraInfo;
}
