package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.entity.*;
import com.example.tourding.direction.repository.*;
import com.example.tourding.external.naver.ApiRouteResponse;
import com.example.tourding.external.naver.NaverMapClient;
import com.example.tourding.external.open_routes_service.ORSCilent;
import com.example.tourding.external.open_routes_service.ORSResponse;
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
    private final ORSCilent orsCilent;
    private final UserRepository userRepository;
    private final RouteSummaryRepository routeSummaryRepository;
    private final RouteGuideRepository routeGuideRepository;
    private final RoutePathRepository routePathRepository;
    private final RouteLocationNameRepository routeLocationNameRepository;


    @Override
    public RouteSummaryRespDto getRoute(RouteRequestDto requestDto) {
        Long userId = requestDto.getUserId();
        String start = requestDto.getStart();
        String goal = requestDto.getGoal();
        String wayPoints = requestDto.getWayPoints();
        String locateName = requestDto.getLocateName();
        String typeCode = requestDto.getTypeCode();
        String[][] locationCodes = parseLocation(start,goal,wayPoints);

        List<String> locationNames = List.of(locateName.split(","));
        List<String> typeCodes = List.of(typeCode.split(","));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));

        ORSResponse orsResponse = orsCilent.getORSDirection(start, goal, wayPoints);
        if(orsResponse.getFeatures() == null) {
            throw new RuntimeException("API 응답에 features가 없음");
        }

        RouteSummaryRespDto routeSummaryRespDto = convertORSResponseToRouteSummaryRespDto(orsResponse, locationNames, locationCodes, typeCodes);
        
        // 사용자의 기존 경로가 있으면 덮어쓰기, 없으면 새로 생성
        if(user.getSummary() != null) {
            RouteSummary updatedSummary = replaceUserRoute(user.getSummary().getId(), routeSummaryRespDto, locationNames, user, locationCodes, typeCodes, start);
            user.setSummary(updatedSummary);
            userRepository.save(user);
        } else {
            RouteSummary newSummary = createNewRouteSummary(routeSummaryRespDto, locationNames, locationCodes, typeCodes, start);
            newSummary.setUser(user);
            user.setSummary(newSummary);
            routeSummaryRepository.save(newSummary);
            userRepository.save(user);
        }

        return routeSummaryRespDto;
    }

    @Transactional
    public RouteSummary replaceUserRoute(Long summaryId, RouteSummaryRespDto dto, List<String> locationNames, User user, String[][] locationCodes, List<String> typeCodes, String start) {
        // 1단계: 기존 경로 데이터 삭제
        deleteUserRoute(summaryId, user);
        
        // 2단계: 새로운 경로 데이터 생성
        RouteSummary newSummary = createUserRoute(dto, locationNames, user, locationCodes, typeCodes, start);
        
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
        routeLocationNameRepository.deleteBySummaryId(summaryId);
        routeSummaryRepository.deleteById(summaryId);

        routeSummaryRepository.flush();

        user.setSummary(null);
    }
    
    @Transactional
    public RouteSummary createUserRoute(RouteSummaryRespDto dto, List<String> locationNames, User user, String[][] locationCodes, List<String> typeCodes, String start) {
        // 새로운 summary 생성 및 저장
        RouteSummary newSummary = createNewRouteSummary(dto, locationNames,locationCodes, typeCodes, start);
        newSummary.setUser(user);
        return routeSummaryRepository.save(newSummary);
    }
    
    private RouteSummary createNewRouteSummary(RouteSummaryRespDto routeSummaryRespDto, List<String> locationNames, String[][] locationCodes, List<String> typeCodes, String start) {
        String[] codes = start.split(",");

        RouteSummary routeSummary = new RouteSummary();


        RouteGuide startGuide = RouteGuide.builder()
                .sequenceNum(0)
                .distance(0)
                .duration(0)
                .instructions("출발지")
                .locationName(locationNames.get(0))
                .pointIndex(0)
                .type(11)
                .lon(codes[0])
                .lat(codes[1])
                .build();
        routeSummary.addRouteGuide(startGuide);

        routeSummaryRespDto.getRouteGuides().forEach(guideDto -> {
            String locationName = "";
            String instructions = "";

            if (guideDto.getInstructions().contains("Arrive at your destination")) {
                if (guideDto.getInstructions().contains("right")) {
                    instructions = "목적지가 오른쪽에 있습니다.";
                } else if (guideDto.getInstructions().contains("left")) {
                    instructions = "목적지가 왼쪽에 있습니다.";
                } else {
                    instructions = "목적지";
                }

                locationName = locationNames.get(locationNames.size() - 1);
            } else {
                instructions = guideDto.getInstructions();
                locationName = guideDto.getLocationName();
            }
            RouteGuide routeGuide = RouteGuide.builder()
                    .sequenceNum(guideDto.getSequenceNum()+1)
                    .distance(guideDto.getDistance())
                    .duration(guideDto.getDuration())
                    .instructions(instructions)
                    .pointIndex(guideDto.getPointIndex())
                    .lat(guideDto.getLat())
                    .lon(guideDto.getLon())
                    .type(guideDto.getType() == 11 ? 6 : guideDto.getType())
                    .locationName(locationName)
                    .build();
            routeSummary.addRouteGuide(routeGuide);
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

                    String typeCode = i == 0? "" :
                            i == locationNames.size() -1 ? "" : typeCodes.get(i-1);

                    RouteLocationName routeLocationName = RouteLocationName.builder()
                            .name(name)
                            .type(type)
                            .typeCode(typeCode)
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
                    List<RouteGuide> routeGuides = routeGuideRepository.findRouteGuideBySummaryIdOrderBySequenceNumAsc(summary.getId());
                    return IntStream.range(0, routeGuides.size())
                            .mapToObj(i -> RouteGuideRespDto.fromEntity(routeGuides.get(i), i))
                            .collect(Collectors.toList());
                })
                .orElse(Collections.emptyList());
    }

    public List<RoutePathRespDto> getPathByUserId(Long userId) {
        return routeSummaryRepository.findRouteSummaryByUserId(userId)
                .map(summary -> {
                    List<RoutePath> routePaths = routePathRepository.findRoutePathBySummaryIdOrderBySequenceNumAsc(summary.getId());
                    return IntStream.range(0, routePaths.size())
                            .mapToObj(i -> RoutePathRespDto.fromEntity(routePaths.get(i), i))
                            .collect(Collectors.toList());

                })
                .orElse(Collections.emptyList());
    }

    public List<RouteLocationNameRespDto> getLocationNameByUserId(Long userId) {
        return routeSummaryRepository.findRouteSummaryByUserId(userId)
                .map(summary -> {
                    List<RouteLocationName> routeLocationNames = routeLocationNameRepository.findRouteLocationNameBySummaryIdOrderBySequenceNumAsc(summary.getId());
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

        // 위치 이름 변환
        List<RouteLocationNameRespDto> locationNames = IntStream.range(0, summary.getRouteLocationNames().size())
                .mapToObj(i -> RouteLocationNameRespDto.fromEntity(summary.getRouteLocationNames().get(i), i))
                .collect(Collectors.toList());

        RouteSummaryRespDto dto = RouteSummaryRespDto.builder()
                .routeGuides(guides)
                .routePaths(paths)
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

    private RouteSummaryRespDto convertORSResponseToRouteSummaryRespDto(
            ORSResponse orsResponse,
            List<String> locationNames,
            String[][] locationCodes,
            List<String> typeCodes) {

        // features[0] 기준으로 파싱 어차피 하나밖에 없음
        var feature = orsResponse.getFeatures().get(0);

        // routeGuides 생성 원래 Guide를 segments.steps에서 파싱
        List<RouteGuideRespDto> routeGuides = new ArrayList<>();
        int seq = 0;
        var steps = feature.getProperties().getSegments().get(0).getSteps();
        var coordinates = feature.getGeometry().getCoordinates();
        for (var step : steps) {
            routeGuides.add(RouteGuideRespDto.builder()
                    .sequenceNum(seq++)
                    .distance((int) step.getDistance())
                    .duration((int) (step.getDuration() * 1000))
                    .instructions(step.getInstruction()) // 그대로
                    .locationName("-".equals(step.getName()) ? "" : step.getName())
                    .pointIndex(step.getWay_points().get(0))
                    .type(step.getType())
                    .lon(String.valueOf(coordinates.get(step.getWay_points().get(0)).get(0))) // 좌표는 path에서 매핑
                    .lat(String.valueOf(coordinates.get(step.getWay_points().get(0)).get(1)))
                    .build());
        }

        // RoutePathRespDto는 geometry.coordinates 에서 파싱
        List<RoutePathRespDto> routePaths = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i++) {
            List<Double> coord = coordinates.get(i); // [lon, lat]
            routePaths.add(RoutePathRespDto.builder()
                    .sequenceNum(i)
                    .lon(String.valueOf(coord.get(0)))
                    .lat(String.valueOf(coord.get(1)))
                    .build());
        }

        // routeSection은 properties 에서 파싱
        List<RouteSectionRespDto> routeSections = IntStream.range(0, feature.getProperties().getSegments().size())
                .mapToObj(i -> {
                    var seg = feature.getProperties().getSegments().get(i);
                    return RouteSectionRespDto.builder()
                            .sequenceNum(i)
                            .name("구간 " + (i + 1))
                            .congestion(0)
                            .distance((int) seg.getDistance())
                            .speed(0)
                            .pointCount(seg.getSteps().size())
                            .pointIndex(0)
                            .build();
                }).collect(Collectors.toList());

        List<RouteLocationNameRespDto> routeLocations = IntStream.range(0, locationNames.size())
                .mapToObj(i -> RouteLocationNameRespDto.builder()
                        .sequenceNum(i + 1)
                        .name(locationNames.get(i))
                        .type(i == 0 ? "Start" : (i == locationNames.size() - 1 ? "Goal" : "WayPoint"))
                        .typeCode(i == 0 ? "" : (i == locationNames.size() - 1 ? "" : typeCodes.get(i - 1)))
                        .lon(locationCodes[i][0])
                        .lat(locationCodes[i][1])
                        .build()
                ).collect(Collectors.toList());


        return RouteSummaryRespDto.builder()
                .routeGuides(routeGuides)
                .routePaths(routePaths)
                .routeSections(routeSections)
                .routeLocations(routeLocations)
                .build();
    }


}
