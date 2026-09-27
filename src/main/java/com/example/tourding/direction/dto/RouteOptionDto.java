package com.example.tourding.direction.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RouteOptionDto {
    private String cyclingProfile;
    private Boolean fastRoute;
    /** ORS preference: fastest, shortest, recommended. Null keeps legacy fastRoute behavior. */
    private String routePreference;
    private Boolean avoidSteps;
    private Boolean avoidFords;
    private Boolean avoidFerries;
    private String skillLevel;

    public static RouteOptionDto defaults() {
        return RouteOptionDto.builder()
                .cyclingProfile("cycling-regular")
                .fastRoute(true)
                .avoidSteps(true)
                .avoidFords(true)
                .avoidFerries(false)
                .skillLevel("BEGINNER")
                .build();
    }
}
