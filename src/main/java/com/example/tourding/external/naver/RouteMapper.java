package com.example.tourding.external.naver;

import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.entity.*;

import java.util.List;

public class RouteMapper {

    public static RouteSummary toEntity(RouteSummaryRespDto dto) {
        if (dto == null) return null;

        RouteSummary summary = RouteSummary.builder()
                .departureTime(dto.getDepartureTime())
                .distance(dto.getDistance())
                .duration(dto.getDuration())
                .fuelPrice(dto.getFuelPrice())
                .taxiFare(dto.getTaxiFare())
                .startLat(dto.getStartLat())
                .startLon(dto.getStartLon())
                .goalLat(dto.getGoalLat())
                .goalLon(dto.getGoalLon())
                .goalDir(dto.getGoalDir())
                .bboxSwLat(dto.getBboxSwLat())
                .bboxSwLon(dto.getBboxSwLon())
                .bboxNeLat(dto.getBboxNeLat())
                .bboxNeLon(dto.getBboxNeLon())
                .build();

        // Guide 리스트 변환 및 세팅
        List<RouteGuide> guides = dto.getRouteGuides().stream()
                .map(RouteMapper::toGuideEntity)
                .toList();

        // Section 리스트 변환 및 세팅
        List<RouteSection> sections = dto.getRouteSections().stream()
                .map(RouteMapper::toSectionEntity)
                .toList();

        // Path 리스트 변환 및 세팅
        List<RoutePath> pathPoints = dto.getRoutePaths().stream()
                .map(RouteMapper::toPathPointEntity)
                .toList();

        List<RouteLocationName> locations = dto.getRouteLocations().stream()
                .map(RouteMapper::toLocationNameEntity)
                .toList();

        // 연관관계 설정
        guides.forEach(g -> g.setSummary(summary));
        sections.forEach(s -> s.setSummary(summary));
        pathPoints.forEach(p -> p.setSummary(summary));
        locations.forEach(l -> l.setSummary(summary));

        summary.setRouteGuides(guides);
        summary.setRouteSections(sections);
        summary.setRoutePaths(pathPoints);
        summary.setRouteLocationNames(locations);

        return summary;
    }

    private static RouteGuide toGuideEntity(RouteGuideRespDto dto) {
        return RouteGuide.builder()
                .distance(dto.getDistance())
                .duration(dto.getDuration())
                .instructions(dto.getInstructions())
                .pointIndex(dto.getPointIndex())
                .type(dto.getType())
                .sequenceNum(dto.getSequenceNum())
                .build();
    }

    private static RouteSection toSectionEntity(RouteSectionRespDto dto) {
        return RouteSection.builder()
                .name(dto.getName())
                .distance(dto.getDistance())
                .pointCount(dto.getPointCount())
                .pointIndex(dto.getPointIndex())
                .speed(dto.getSpeed())
                .congestion(dto.getCongestion())
                .sequenceNum(dto.getSequenceNum())
                .build();
    }

    private static RoutePath toPathPointEntity(RoutePathRespDto dto) {
        return RoutePath.builder()
                .lat(dto.getLat())
                .lon(dto.getLon())
                .sequenceNum(dto.getSequenceNum())
                .build();
    }

    private static RouteLocationName toLocationNameEntity(RouteLocationNameRespDto dto) {
        return RouteLocationName.builder()
                .name(dto.getName())
                .type(dto.getType())
                .sequenceNum(dto.getSequenceNum())
                .build();
    }
}
