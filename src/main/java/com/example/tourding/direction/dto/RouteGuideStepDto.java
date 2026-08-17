package com.example.tourding.direction.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteGuideStepDto {
    private Integer sequenceNum;
    private Integer distance;
    private Integer duration;
    private String instructions;
    private String locationName;
    private Integer pointIndex;
    private Integer type;
    private String lon;
    private String lat;
}
