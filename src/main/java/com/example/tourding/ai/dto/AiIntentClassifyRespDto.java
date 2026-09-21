package com.example.tourding.ai.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiIntentClassifyRespDto {
    private String intent;
    private Double confidence;
    private String routeAction;
    private Map<String, Double> weightUpdate;
    private String explanation;
    private List<String> waypointQueries;
    private String waypointMode;
}
