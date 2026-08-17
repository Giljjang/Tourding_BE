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
    private Boolean avoidSteps;
    private Boolean avoidFords;
    private String skillLevel;

    public static RouteOptionDto defaults() {
        return RouteOptionDto.builder()
                .cyclingProfile("cycling-regular")
                .fastRoute(true)
                .avoidSteps(true)
                .avoidFords(true)
                .skillLevel("BEGINNER")
                .build();
    }
}
