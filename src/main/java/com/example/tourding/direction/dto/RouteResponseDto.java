package com.example.tourding.direction.dto;


import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RouteResponseDto {
    private LocalDateTime departureTime;
    private Integer distance; // 전체 경로 거리
    private Integer duration; // 전체 경로 소요시간
    private Integer fuelPrice; // 주유 소모량
    private Integer taxiFare; // 택시요금
    private Integer tollFare; // 톨게이트 비용

    private Double startLon; // 시작지점 경도
    private Double startLat; // 시작지점 위도
    private Double goalLon; // 도착지점 경도
    private Double goalLat; // 도착지점 위도
    private Integer goalDir; // 경로상 진행방향을 중심으로 설정한 도착지의 위치를 나타낸 숫자 (0: 전방, 1:왼쪽, 2:오른쪽(

    // bbox : 전체 경로 경계 영역
    private Double bboxSwLon; // 왼쪽 아래 경도
    private Double bboxSwLat; // 왼쪽 아래 위도
    private Double bboxNeLon; // 오른쪽 위 경도
    private Double bboxNeLat; // 오른쪽 위 위도

    private List<RouteSectionRespDto> routeSections;
    private List<RouteGuideRespDto> routeGuides;
    private List<RoutePathRespDto> routePaths;

    public RouteResponseDto(RouteResponseDto other) {

    }
}
