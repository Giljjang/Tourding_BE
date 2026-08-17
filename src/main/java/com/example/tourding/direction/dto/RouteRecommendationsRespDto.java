package com.example.tourding.direction.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteRecommendationsRespDto {
    private List<RouteGuideRespDto> routes;
}
