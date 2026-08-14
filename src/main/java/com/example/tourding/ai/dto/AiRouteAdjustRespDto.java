package com.example.tourding.ai.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiRouteAdjustRespDto {
    private Long requestId;
    private String transcript;
    private String intent;
    private String action;
    private String status;
    private String rejectionReason;
    private Map<String, Double> weightUpdate;
    private Long selectedCandidateId;
    private List<RouteCandidateRespDto> candidates;
}
