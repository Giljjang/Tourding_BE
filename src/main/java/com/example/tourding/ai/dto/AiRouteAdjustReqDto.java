package com.example.tourding.ai.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiRouteAdjustReqDto {
    private Long userId;
    private Long routeSummaryId;
    private Double currentLon;
    private Double currentLat;
    private String message;
}
