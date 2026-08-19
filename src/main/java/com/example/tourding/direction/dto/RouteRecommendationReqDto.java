package com.example.tourding.direction.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RouteRecommendationReqDto {
    private Long userId;
    private String start;
    private String goal;
    private Boolean isUsed;
    private RouteOptionDto routeOption;
}
