package com.example.tourding.ai.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RouteCandidateRespDto {
    private Long candidateId;
    private Integer rank;
    private Double score;
    private Double distance;
    private Double duration;
    private Double ascent;
    private Double descent;
    private RouteScoreDetailDto scoreDetail;
    private String reason;
}
