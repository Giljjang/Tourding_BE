package com.example.tourding.external.open_routes_service;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ORSRouteAnalysisRequest {
    private String profile;
    private String preference;
    private List<List<Double>> coordinates;
    private Integer steepnessDifficulty;
    private Boolean avoidSteps;
    private Boolean avoidFords;

    public Map<String, Object> toRequestBody() {
        String resolvedPreference = preference == null ? "recommended" : preference;
        int resolvedSteepness = steepnessDifficulty == null ? 1 : steepnessDifficulty;
        List<String> avoidFeatures = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(avoidSteps)) {
            avoidFeatures.add("steps");
        }
        if (Boolean.TRUE.equals(avoidFords)) {
            avoidFeatures.add("fords");
        }

        return Map.of(
                "coordinates", coordinates,
                "preference", resolvedPreference,
                "elevation", true,
                "instructions", true,
                "maneuvers", true,
                "geometry", true,
                "geometry_simplify", false,
                "extra_info", List.of("steepness", "suitability", "surface", "waytype"),
                "attributes", List.of("avgspeed", "detourfactor", "percentage"),
                "options", Map.of(
                        "avoid_features", avoidFeatures,
                        "profile_params", Map.of(
                                "weightings", Map.of("steepness_difficulty", resolvedSteepness)
                        )
                )
        );
    }
}
