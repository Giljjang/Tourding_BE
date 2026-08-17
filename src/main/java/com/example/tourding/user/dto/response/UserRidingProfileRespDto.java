package com.example.tourding.user.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRidingProfileRespDto {
    private Long userId;
    private String cyclingProfile;
    private Boolean fastRoute;
    private Boolean avoidSteps;
    private Boolean avoidFords;
    private String skillLevel;
}
