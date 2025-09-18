package com.example.tourding.direction.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RouteSummaryRespDto {

    @Builder.Default
    private List<RouteGuideRespDto> routeGuides = new ArrayList<>();
    @Builder.Default
    private List<RoutePathRespDto> routePaths = new ArrayList<>();
    @Builder.Default
    private List<RouteLocationNameRespDto> routeLocations = new ArrayList<>();

}
