package com.example.tourding.ai.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RouteScoreDetailDto {
    private Double comfort;
    private Double flatness;
    private Double surface;
    private Double waytype;
    private Double efficiency;
}
