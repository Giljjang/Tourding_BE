package com.example.tourding.direction.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteSurfaceSummaryDto {
    private RouteSurfaceType type;
    private Double percentage;
    private Double distance;
}
