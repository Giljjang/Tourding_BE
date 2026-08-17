package com.example.tourding.ai.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRidingProfileReqDto {
    private Long userId;
    private String cyclingProfile;
    private String skillLevel;
    private Boolean fastRoute;
    private Boolean avoidSteps;
    private Boolean avoidFords;
    private Boolean avoidHills;
    private Boolean preferPaved;
    private Boolean preferBikeRoad;
    private Boolean avoidMainRoad;
}
