package com.example.tourding.direction.dto;


import com.example.tourding.direction.entity.RouteLocationName;
import com.example.tourding.external.naver.ApiRouteResponse;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    public static RouteSummaryRespDto from(ApiRouteResponse.Traoptimal tra, List<String> locationNames, String[][] locationCodes, List<String> typeCodes) {
        var summary = tra.getSummary();

        List<RouteGuideRespDto> guideDtos = new ArrayList<>();
        if (tra.getGuide() != null && !tra.getGuide().isEmpty()) {
            for(int i=0; i<tra.getGuide().size(); i++) {
                guideDtos.add(RouteGuideRespDto.from(tra.getGuide().get(i), i, locationNames));
            }
        }

        List<RoutePathRespDto> pathDtos = new ArrayList<>();
        if (tra.getPath() != null && !tra.getPath().isEmpty()) {
            for(int i=0; i<tra.getPath().size(); i++) {
                pathDtos.add(RoutePathRespDto.from(tra.getPath().get(i), i));
            }
        }

        if(!guideDtos.isEmpty() && !pathDtos.isEmpty()) {
            Map<Integer, RoutePathRespDto> pathDtoMap = pathDtos.stream()
                    .collect(Collectors.toMap(RoutePathRespDto::getSequenceNum, p->p));

            for(RouteGuideRespDto guideRespDto : guideDtos) {
                RoutePathRespDto routePathDto = pathDtoMap.get(guideRespDto.getPointIndex());
                if(routePathDto != null) {
                    guideRespDto.setLat(routePathDto.getLat());
                    guideRespDto.setLon(routePathDto.getLon());
                }
            }
        }

        List<RouteLocationNameRespDto> routeLocationNameRespDtos = IntStream.range(0, locationNames.size())
                .mapToObj(i -> {
                    String name = locationNames.get(i).trim();
                    String type = i == 0? "Start" :
                            i == locationNames.size() - 1 ? "Goal" : "WayPoint";
                    String typeCode = i == 0? "" :
                            i == locationNames.size() -1 ? "" : typeCodes.get(i-1);
                    return RouteLocationNameRespDto.builder()
                            .name(name)
                            .type(type)
                            .typeCode(typeCode)
                            .lon(locationCodes[i][0])
                            .lat(locationCodes[i][1])
                            .sequenceNum(i)
                            .build();
                })
                .toList();


        return RouteSummaryRespDto.builder()
                .routeGuides(guideDtos)
                .routePaths(pathDtos)
                .routeLocations(routeLocationNameRespDtos)
                .build();
    }
}
