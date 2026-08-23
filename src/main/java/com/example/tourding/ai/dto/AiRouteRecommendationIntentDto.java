package com.example.tourding.ai.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRouteRecommendationIntentDto {
    @Builder.Default
    private List<String> waypointNames = List.of();
    private Integer targetDifficulty;
    private Boolean avoidConstruction;
    private Boolean avoidSteps;
    private Boolean avoidIce;
    private Double maxDistanceKm;
    private Map<String, Double> weightUpdate;
    private String explanation;
    private Boolean supported;

    public boolean isSupported() {
        return Boolean.TRUE.equals(supported);
    }
}
