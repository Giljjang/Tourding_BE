package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.entity.*;
import com.example.tourding.direction.repository.*;
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

    @Override
    public RouteSummaryRespDto getRoute(RouteRequestDto requestDto) {
        User user = userRepository.findById(requestDto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));

        // OpenAPI 호출해서 Dto 생성
        ORSResponse orsResponse = orsCilent.getORSDirection(
                requestDto.getStart(),
                requestDto.getGoal(),
                requestDto.getWayPoints()
        );
        RouteSummaryRespDto dto = convertORSResponseToRouteSummaryRespDto(
                orsResponse,
                List.of(requestDto.getLocateName().split(",")),
                parseLocation(requestDto.getStart(), requestDto.getGoal(), requestDto.getWayPoints()),
                List.of(requestDto.getTypeCode().split(","))
        );

        RouteSummary summary;
        if (user.getSummary() != null) {
            summary = user.getSummary(); // 기존 엔티티 가져오기
        } else {
            summary = new RouteSummary();
            summary.setUser(user);
        }
        summary.setStart(requestDto.getStart());
        summary.setGoal(requestDto.getGoal());
        summary.setWayPoints(requestDto.getWayPoints());
        summary.setLocateName(requestDto.getLocateName());
        summary.setTypeCode(requestDto.getTypeCode());

        routeSummaryRepository.save(summary);

        return dto;
    }
    
    @Transactional
    public void deleteUserRoute(Long summaryId, User user) {
        RouteSummary existingSummary = routeSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new EntityNotFoundException("기존 경로 요약을 찾을 수 없습니다."));

        routeSummaryRepository.deleteById(summaryId);

        routeSummaryRepository.flush();

        user.setSummary(null);
    }

    @Transactional(readOnly = true)
    public RouteSummaryRespDto getRouteSummaryByUserId(Long userId) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));

        ORSResponse orsResponse = orsCilent.getORSDirection(
                summary.getStart(),
                summary.getGoal(),
                summary.getWayPoints()
        );

        List<String> locationNames = List.of(summary.getLocateName().split(","));
        String[][] locationCodes = parseLocation(summary.getStart(), summary.getGoal(), summary.getWayPoints());

        return convertORSResponseToRouteSummaryRespDto(
                orsResponse,
                locationNames,
                locationCodes,
                Collections.emptyList() // typeCode 저장 여부에 따라 수정
        );
    }

    @Transactional(readOnly = true)
    public List<RouteGuideRespDto> getGuideByUserId(Long userId) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));

        ORSResponse orsResponse = orsCilent.getORSDirection(
                summary.getStart(),
                summary.getGoal(),
                summary.getWayPoints()
        );

        return convertToRouteGuides(
                orsResponse,
                List.of(summary.getLocateName().split(",")),
                parseLocation(summary.getStart(), summary.getGoal(), summary.getWayPoints()),
                List.of(summary.getTypeCode().split(","))
        );
    }

    @Transactional(readOnly = true)
    public List<RoutePathRespDto> getPathByUserId(Long userId) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));

        ORSResponse orsResponse = orsCilent.getORSDirection(
                summary.getStart(),
                summary.getGoal(),
                summary.getWayPoints()
        );

        return convertToRoutePaths(orsResponse);
    }

    @Transactional(readOnly = true)
    public List<RouteLocationNameRespDto> getLocationNameByUserId(Long userId) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));

        return convertToLocationNames(
                List.of(summary.getLocateName().split(",")),
                parseLocation(summary.getStart(), summary.getGoal(), summary.getWayPoints()),
                List.of(summary.getTypeCode().split(","))
        );
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

        List<RouteGuideRespDto> routeGuides = convertToRouteGuides(orsResponse, locationNames, locationCodes, typeCodes);
        List<RoutePathRespDto> routePaths = convertToRoutePaths(orsResponse);
        List<RouteLocationNameRespDto> routeLocations = convertToLocationNames(locationNames, locationCodes, typeCodes);

        return RouteSummaryRespDto.builder()
                .routeGuides(routeGuides)
                .routePaths(routePaths)
                .routeLocations(routeLocations)
                .build();
    }


    private List<RouteGuideRespDto> convertToRouteGuides(
            ORSResponse orsResponse,
            List<String> locationNames,
            String[][] locationCodes,
            List<String> typeCodes) {

        List<RouteGuideRespDto> routeGuides = new ArrayList<>();
        List<ORSResponse.ORSFeatures> features = orsResponse.getFeatures();
        ORSResponse.ORSFeatures firstFeature = features.get(0);

        List<List<Double>> coordinates = firstFeature.getGeometry().getCoordinates();
        List<ORSResponse.ORSSegment> segments = firstFeature.getProperties().getSegments();

        // 출발지 직접 추가
        routeGuides.add(RouteGuideRespDto.builder()
                .sequenceNum(0)
                .distance(0)
                .duration(0)
                .instructions("출발지")
                .locationName(locationNames.get(0))
                .pointIndex(0)
                .type(11)
                .lon(locationCodes[0][0])
                .lat(locationCodes[0][1])
                .build());

        int seq = 1;
        int locationNameIndex = 1;
        int totalSteps = segments.stream().mapToInt(s -> s.getSteps().size()).sum();
        int currentIndex = 0;

        for (ORSResponse.ORSSegment segment : segments) {
            for (ORSResponse.ORSStep step : segment.getSteps()) {
                String instructions;
                String locationName;
                int type;

                // 목적지
                if (step.getInstruction().contains("Arrive at your destination") && currentIndex == totalSteps-1) {
                    if (step.getInstruction().contains("right")) {
                        instructions = "목적지가 오른쪽에 있습니다.";
                    } else if (step.getInstruction().contains("left")) {
                        instructions = "목적지가 왼쪽에 있습니다.";
                    } else {
                        instructions = "목적지";
                    }
                    type = 10;
                    locationName = locationNames.get(locationNames.size() - 1);

                    // 경유지
                } else if (step.getInstruction().contains("Arrive at")) {
                    if (step.getInstruction().contains("right")) {
                        instructions = "경유지가 오른쪽에 있습니다.";
                    } else if (step.getInstruction().contains("left")) {
                        instructions = "경유지가 왼쪽에 있습니다.";
                    } else {
                        instructions = "경유지";
                    }
                    type = 9;
                    locationName = locationNames.get(locationNameIndex++);
                } else {
                    // 일반 안내
                    instructions = step.getInstruction();
                    locationName = "-".equals(step.getName()) ? "" : step.getName();
                    type = step.getType() == 11 ? 6 : step.getType();
                }

                // instructions에 Head 뭐시기 있고 + type=6 은 제외
                if (instructions.contains("Head") && type == 6) {
                    currentIndex++;
                    continue;
                }

                routeGuides.add(RouteGuideRespDto.builder()
                        .sequenceNum(seq++)
                        .distance((int) step.getDistance())
                        .duration((int) (step.getDuration() * 1000))
                        .instructions(instructions)
                        .locationName(locationName)
                        .pointIndex(step.getWay_points().get(0))
                        .type(type)
                        .lon(String.valueOf(coordinates.get(step.getWay_points().get(0)).get(0)))
                        .lat(String.valueOf(coordinates.get(step.getWay_points().get(0)).get(1)))
                        .build());
                currentIndex++;
            }
        }
        return routeGuides;
    }

    private List<RoutePathRespDto> convertToRoutePaths(ORSResponse orsResponse) {
        ORSResponse.ORSFeatures feature = orsResponse.getFeatures().get(0);
        List<List<Double>> coordinates = feature.getGeometry().getCoordinates();

        List<RoutePathRespDto> routePaths = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i++) {
            List<Double> coord = coordinates.get(i);
            routePaths.add(RoutePathRespDto.builder()
                    .sequenceNum(i)
                    .lon(String.valueOf(coord.get(0)))
                    .lat(String.valueOf(coord.get(1)))
                    .build());
        }
        return routePaths;
    }

    private List<RouteLocationNameRespDto> convertToLocationNames(
            List<String> locationNames,
            String[][] locationCodes,
            List<String> typeCodes) {

        return IntStream.range(0, locationNames.size())
                .mapToObj(i -> RouteLocationNameRespDto.builder()
                        .sequenceNum(i + 1)
                        .name(locationNames.get(i))
                        .type(i == 0 ? "Start" : (i == locationNames.size() - 1 ? "Goal" : "WayPoint"))
                        .typeCode(i == 0 ? "" : (i == locationNames.size() - 1 ? "" : typeCodes.get(i - 1)))
                        .lon(locationCodes[i][0])
                        .lat(locationCodes[i][1])
                        .build()
                ).collect(Collectors.toList());
    }

}
