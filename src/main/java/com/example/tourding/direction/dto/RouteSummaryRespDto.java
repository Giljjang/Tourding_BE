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
    private LocalDateTime departureTime;
    private Integer distance; // 전체 경로 거리
    private Integer duration; // 전체 경로 소요시간
    private Integer fuelPrice; // 주유 소모량
    private Integer taxiFare; // 택시요금
    private Integer tollFare; // 톨게이트 비용

    private String startLon; // 시작지점 경도
    private String startLat; // 시작지점 위도
    private String goalLon; // 도착지점 경도
    private String goalLat; // 도착지점 위도
    private Integer goalDir; // 경로상 진행방향을 중심으로 설정한 도착지의 위치를 나타낸 숫자 (0: 전방, 1:왼쪽, 2:오른쪽(

    // bbox : 전체 경로 경계 영역
    private String bboxSwLon; // 왼쪽 아래 경도
    private String bboxSwLat; // 왼쪽 아래 위도
    private String bboxNeLon; // 오른쪽 위 경도
    private String bboxNeLat; // 오른쪽 위 위도

    @Builder.Default
    private List<RouteSectionRespDto> routeSections = new ArrayList<>();
    @Builder.Default
    private List<RouteGuideRespDto> routeGuides = new ArrayList<>();
    @Builder.Default
    private List<RoutePathRespDto> routePaths = new ArrayList<>();
    @Builder.Default
    private List<RouteLocationNameRespDto> routeLocations = new ArrayList<>();

    public static RouteSummaryRespDto from(ApiRouteResponse.Traavoidcaronly tra, List<String> locationNames, String[][] locationCodes, List<String> typeCodes) {
        var summary = tra.getSummary();

        List<RouteGuideRespDto> guideDtos = new ArrayList<>();
        if (tra.getGuide() != null && !tra.getGuide().isEmpty()) {
            for(int i=0; i<tra.getGuide().size(); i++) {
                guideDtos.add(RouteGuideRespDto.from(tra.getGuide().get(i), i));
            }
        }

        List<RouteSectionRespDto> sectionDtos = new ArrayList<>();
        if (tra.getSection() != null && !tra.getSection().isEmpty()) {
            for(int i=0; i<tra.getSection().size(); i++) {
                sectionDtos.add(RouteSectionRespDto.from(tra.getSection().get(i), i));
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
                .departureTime(LocalDateTime.parse(summary.getDepartureTime()))
                .distance(summary.getDistance())
                .duration(summary.getDuration())
                .fuelPrice(summary.getFuelPrice())
                .taxiFare(summary.getTaxiFare())
                .tollFare(summary.getTollFare())
                .startLon(summary.getStart().getLocation().get(0))
                .startLat(summary.getStart().getLocation().get(1))
                .goalLon(summary.getGoal().getLocation().get(0))
                .goalLat(summary.getGoal().getLocation().get(1))
                .goalDir(summary.getGoal().getDir())
                .bboxSwLon(summary.getBbox().get(0).get(0))
                .bboxSwLat(summary.getBbox().get(0).get(1))
                .bboxNeLon(summary.getBbox().get(1).get(0))
                .bboxNeLat(summary.getBbox().get(1).get(1))
                .routeGuides(guideDtos)
                .routeSections(sectionDtos)
                .routePaths(pathDtos)
                .routeLocations(routeLocationNameRespDtos)
                .build();
    }
}
