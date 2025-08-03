package com.example.tourding.direction.dto;

import com.example.tourding.direction.entity.RoutePath;
import com.example.tourding.direction.entity.RouteSection;
import com.example.tourding.direction.external.ApiRouteResponse;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RouteSectionRespDto {
    private Integer sequenceNum; // 순서 보존용
    private String name; // 도로 이름
    private Integer congestion; // 혼잡도 분류 코드 (0: 값없음, 1: 원활, 2: 서행, 3:혼잡)
    private Integer distance; // (거리 m)
    private Integer speed; // 평균 속도
    private Integer pointCount; // 형상점 수
    private Integer pointIndex; // 경로를 구성하는 좌표의 인덱스

    public static RouteSectionRespDto from(ApiRouteResponse.Section section, int sequenceNum) {
        return RouteSectionRespDto.builder()
                .name(section.getName())
                .distance(section.getDistance())
                .pointCount(section.getPointCount())
                .pointIndex(section.getPointIndex())
                .speed(section.getSpeed())
                .congestion(section.getCongestion())
                .sequenceNum(sequenceNum)
                .build();
    }

    public static RouteSectionRespDto fromEntity(RouteSection routeSection, int sequenceNum) {
        return RouteSectionRespDto.builder()
                .name(routeSection.getName())
                .distance(routeSection.getDistance())
                .pointCount(routeSection.getPointCount())
                .pointIndex(routeSection.getPointIndex())
                .speed(routeSection.getSpeed())
                .congestion(routeSection.getCongestion())
                .sequenceNum(sequenceNum)
                .build();
    }
}
