package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.entity.*;
import com.example.tourding.direction.repository.*;
import com.example.tourding.external.naver.ApiRouteResponse;
import com.example.tourding.external.naver.NaverMapClient;
import com.example.tourding.user.entity.User;
import com.example.tourding.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import java.util.Collections;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor

public class RouteService implements RouteServiceImpl {
    private final NaverMapClient naverMapClient;
    private final UserRepository userRepository;
    private final RouteSummaryRepository routeSummaryRepository;
    private final RouteGuideRepository routeGuideRepository;
    private final RoutePathRepository routePathRepository;
    private final RouteSectionRepository routeSectionRepository;
    private final RouteLocationNameRepository routeLocationNameRepository;
    private final jakarta.persistence.EntityManager entityManager;


    @Override
    public RouteSummaryRespDto getRoute(RouteRequestDto requestDto) {
        Long userId = requestDto.getUserId();
        String start = requestDto.getStart();
        String goal = requestDto.getGoal();
        String wayPoints = requestDto.getWayPoints();
        String locateName = requestDto.getLocateName();
        String[][] locationCodes = parseLocation(start,goal,wayPoints);

        List<String> locationNames = List.of(locateName.split(","));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));

        ApiRouteResponse apiRouteResponse = naverMapClient.getDirection(start, goal, wayPoints);
        if(apiRouteResponse.getRoute() == null) {
            throw new RuntimeException("API 응답에 route가 없음");
        }

        var tra = apiRouteResponse.getRoute().getTraoptimal().get(0);
        if(tra == null) {
            throw new RuntimeException("route안에 traoptimal이 없음");
        }

        RouteSummaryRespDto routeSummaryRespDto = RouteSummaryRespDto.from(tra, locationNames, locationCodes);
        
        // 사용자의 기존 경로가 있으면 덮어쓰기, 없으면 새로 생성
        if(user.getSummary() != null) {
            RouteSummary updatedSummary = replaceUserRoute(user.getSummary().getId(), routeSummaryRespDto, locationNames, user, locationCodes);
            user.setSummary(updatedSummary);
            userRepository.save(user);
        } else {
            RouteSummary newSummary = createNewRouteSummary(routeSummaryRespDto, locationNames, locationCodes);
            newSummary.setUser(user);
            user.setSummary(newSummary);
            routeSummaryRepository.save(newSummary);
            userRepository.save(user);
        }

        return routeSummaryRespDto;
    }

    @Transactional
    public RouteSummary replaceUserRoute(Long summaryId, RouteSummaryRespDto dto, List<String> locationNames, User user, String[][] locationCodes) {
        // 1단계: 기존 경로 데이터 삭제
        deleteUserRoute(summaryId, user);
        
        // 2단계: 새로운 경로 데이터 생성
        RouteSummary newSummary = createUserRoute(dto, locationNames, user, locationCodes);
        
        // 3단계: User 엔티티 업데이트
        user.setSummary(newSummary);
        userRepository.save(user);
        
        return newSummary;
    }
    
    @Transactional
    public void deleteUserRoute(Long summaryId, User user) {
        RouteSummary existingSummary = routeSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new EntityNotFoundException("기존 경로 요약을 찾을 수 없습니다."));

        routeGuideRepository.deleteBySummaryId(summaryId);
        routePathRepository.deleteBySummaryId(summaryId);
        routeSectionRepository.deleteBySummaryId(summaryId);
        routeLocationNameRepository.deleteBySummaryId(summaryId);
        routeSummaryRepository.deleteById(summaryId);

        routeSummaryRepository.flush();

        user.setSummary(null);
    }
    
    @Transactional
    public RouteSummary createUserRoute(RouteSummaryRespDto dto, List<String> locationNames, User user, String[][] locationCodes) {
        // 새로운 summary 생성 및 저장
        RouteSummary newSummary = createNewRouteSummary(dto, locationNames,locationCodes);
        newSummary.setUser(user);
        return routeSummaryRepository.save(newSummary);
    }
    
    private RouteSummary createNewRouteSummary(RouteSummaryRespDto routeSummaryRespDto, List<String> locationNames, String[][] locationCodes) {
        RouteSummary routeSummary = RouteSummary.builder()
                .departureTime(routeSummaryRespDto.getDepartureTime())
                .distance(routeSummaryRespDto.getDistance())
                .duration(routeSummaryRespDto.getDuration())
                .fuelPrice(routeSummaryRespDto.getFuelPrice())
                .taxiFare(routeSummaryRespDto.getTaxiFare())
                .tollFare(routeSummaryRespDto.getTollFare())
                .startLon(routeSummaryRespDto.getStartLon())
                .startLat(routeSummaryRespDto.getStartLat())
                .goalLon(routeSummaryRespDto.getGoalLon())
                .goalLat(routeSummaryRespDto.getGoalLat())
                .goalDir(routeSummaryRespDto.getGoalDir())
                .bboxSwLon(routeSummaryRespDto.getBboxSwLon())
                .bboxSwLat(routeSummaryRespDto.getBboxSwLat())
                .bboxNeLon(routeSummaryRespDto.getBboxNeLon())
                .bboxNeLat(routeSummaryRespDto.getBboxNeLat())
                .build();

        routeSummaryRespDto.getRouteGuides().forEach(guideDto -> {
            RouteGuide routeGuide = RouteGuide.builder()
                    .sequenceNum(guideDto.getSequenceNum())
                    .distance(guideDto.getDistance())
                    .duration(guideDto.getDuration())
                    .instructions(guideDto.getInstructions())
                    .pointIndex(guideDto.getPointIndex())
                    .type(guideDto.getType())
                    .build();
            routeSummary.addRouteGuide(routeGuide);
        });

        routeSummaryRespDto.getRouteSections().forEach(sectionDto -> {
            RouteSection routeSection = RouteSection.builder()
                    .sequenceNum(sectionDto.getSequenceNum())
                    .name(sectionDto.getName())
                    .congestion(sectionDto.getCongestion())
                    .distance(sectionDto.getDistance())
                    .speed(sectionDto.getSpeed())
                    .pointCount(sectionDto.getPointCount())
                    .pointIndex(sectionDto.getPointIndex())
                    .build();
            routeSummary.addRouteSection(routeSection);
        });

        routeSummaryRespDto.getRoutePaths().forEach(pathDto -> {
            RoutePath routePath = RoutePath.builder()
                    .sequenceNum(pathDto.getSequenceNum())
                    .lon(pathDto.getLon())
                    .lat(pathDto.getLat())
                    .build();
            routeSummary.addRoutePathPoint(routePath);
        });

        IntStream.range(0, locationNames.size())
                .forEach(i -> {
                    String name = locationNames.get(i).trim();
                    String type = "";

                    if(i == 0) type = "Start";
                    else if(i == locationNames.size() - 1) type = "Goal";
                    else type = "WayPoint";



                    RouteLocationName routeLocationName = RouteLocationName.builder()
                            .name(name)
                            .type(type)
                            .lon(locationCodes[i][0])
                            .lat(locationCodes[i][1])
                            .sequenceNum(i+1)
                            .build();

                    routeSummary.addRouteLocationName(routeLocationName);
                });
        
        return routeSummary;
    }

    public List<RouteGuideRespDto> getGuideByUserId(Long userId) {
        return routeSummaryRepository.findRouteSummaryByUserId(userId)
                .map(summary -> {
                    List<RouteGuide> routeGuides = routeGuideRepository.findRouteGuideBySummaryId(summary.getId());
                    return IntStream.range(0, routeGuides.size())
                            .mapToObj(i -> RouteGuideRespDto.fromEntity(routeGuides.get(i), i))
                            .collect(Collectors.toList());
                })
                .orElse(Collections.emptyList());
    }

    public List<RoutePathRespDto> getPathByUserId(Long userId) {
        return routeSummaryRepository.findRouteSummaryByUserId(userId)
                .map(summary -> {
                    List<RoutePath> routePaths = routePathRepository.findRoutePathBySummaryId(summary.getId());
                    return IntStream.range(0, routePaths.size())
                            .mapToObj(i -> RoutePathRespDto.fromEntity(routePaths.get(i), i))
                            .collect(Collectors.toList());

                })
                .orElse(Collections.emptyList());
    }

    public List<RouteSectionRespDto> getSectionByUserId(Long userId) {
        return routeSummaryRepository.findRouteSummaryByUserId(userId)
                .map(summary -> {
                    List<RouteSection> routeSections = routeSectionRepository.findRouteSectionBySummaryId(summary.getId());
                    return IntStream.range(0, routeSections.size())
                            .mapToObj(i -> RouteSectionRespDto.fromEntity(routeSections.get(i), i))
                            .collect(Collectors.toList());
                })
                .orElse(Collections.emptyList());


    }

    public List<RouteLocationNameRespDto> getLocationNameByUserId(Long userId) {
        return routeSummaryRepository.findRouteSummaryByUserId(userId)
                .map(summary -> {
                    List<RouteLocationName> routeLocationNames = routeLocationNameRepository.findRouteLocationNameBySummaryId(summary.getId());
                    return IntStream.range(0, routeLocationNames.size())
                            .mapToObj(i -> RouteLocationNameRespDto.fromEntity(routeLocationNames.get(i), i))
                            .collect(Collectors.toList());
                })
                .orElse(Collections.emptyList());
    }

    @Transactional
    public RouteSummaryRespDto getRouteSummaryByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new EntityNotFoundException("사용자 없음"));

        RouteSummary summary = user.getSummary();
        if(summary == null) {
            return RouteSummaryRespDto.builder()
                    .routeGuides(Collections.emptyList())
                    .routePaths(Collections.emptyList())
                    .routeSections(Collections.emptyList())
                    .routeLocations(Collections.emptyList())
                    .build();
        }

        // 가이드 변환
        List<RouteGuideRespDto> guides = IntStream.range(0, summary.getRouteGuides().size())
                .mapToObj(i -> RouteGuideRespDto.fromEntity(summary.getRouteGuides().get(i), i))
                .collect(Collectors.toList());

        // 경로 포인트 변환
        List<RoutePathRespDto> paths = IntStream.range(0, summary.getRoutePaths().size())
                .mapToObj(i -> RoutePathRespDto.fromEntity(summary.getRoutePaths().get(i), i))
                .collect(Collectors.toList());

        // 구간 변환
        List<RouteSectionRespDto> sections = IntStream.range(0, summary.getRouteSections().size())
                .mapToObj(i -> RouteSectionRespDto.fromEntity(summary.getRouteSections().get(i), i))
                .collect(Collectors.toList());

        // 위치 이름 변환
        List<RouteLocationNameRespDto> locationNames = IntStream.range(0, summary.getRouteLocationNames().size())
                .mapToObj(i -> RouteLocationNameRespDto.fromEntity(summary.getRouteLocationNames().get(i), i))
                .collect(Collectors.toList());

        RouteSummaryRespDto dto = RouteSummaryRespDto.builder()
                .departureTime(summary.getDepartureTime())
                .distance(summary.getDistance())
                .duration(summary.getDuration())
                .fuelPrice(summary.getFuelPrice())
                .taxiFare(summary.getTaxiFare())
                .tollFare(summary.getTollFare())
                .startLon(summary.getStartLon())
                .startLat(summary.getStartLat())
                .goalLon(summary.getGoalLon())
                .goalLat(summary.getGoalLat())
                .goalDir(summary.getGoalDir())
                .bboxSwLon(summary.getBboxSwLon())
                .bboxSwLat(summary.getBboxSwLat())
                .bboxNeLon(summary.getBboxNeLon())
                .bboxNeLat(summary.getBboxNeLat())
                .routeGuides(guides)
                .routePaths(paths)
                .routeSections(sections)
                .routeLocations(locationNames)
                .build();

        return dto;
    }

    private String[][] parseLocation(String start, String goal, String wayPoints) {
        List<String[]> locationCodes = new ArrayList<>();

        locationCodes.add(start.split(","));

        if (wayPoints != null && !wayPoints.trim().isEmpty()) {
            String[] wayPointArray = wayPoints.split("\\|");
            for (String wayPoint : wayPointArray) {
                if (!wayPoint.trim().isEmpty()) {
                    locationCodes.add(wayPoint.split(","));
                }
            }
        }

        locationCodes.add(goal.split(","));

        return locationCodes.toArray(new String[0][]);
    }
}
