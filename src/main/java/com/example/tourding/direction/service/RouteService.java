package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.entity.*;
import com.example.tourding.direction.repository.*;
import com.example.tourding.external.kakao.KakaoClient;
import com.example.tourding.external.kakao.KakaoSearchResponse;
import com.example.tourding.external.open_routes_service.ORSCilent;
import com.example.tourding.external.open_routes_service.ORSResponse;
import com.example.tourding.external.riding_course.RidingCourseClient;
import com.example.tourding.external.riding_course.RidingCourseResponse;
import com.example.tourding.tourApi.dto.SearchAreaRespDto;
import com.example.tourding.tourApi.dto.SearchLocationDto;
import com.example.tourding.tourApi.service.TourApiService;
import com.example.tourding.user.entity.User;
import com.example.tourding.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import java.util.Collections;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor

public class RouteService implements RouteServiceImpl {
    private final ORSCilent orsCilent;
    private final KakaoClient kakaoClient;
    private final RidingCourseClient ridingCourseClient;
    private final TourApiService tourApiService;
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
                requestDto.getIsUsed(),
                orsResponse
        );

        RouteSummary summary = routeSummaryRepository
                .findRouteSummaryByUserIdAndIsUsed(user.getId(), requestDto.getIsUsed())
                .orElse(new RouteSummary());

        summary.setUser(user);
        summary.setStart(requestDto.getStart());
        summary.setGoal(requestDto.getGoal());
        summary.setWayPoints(requestDto.getWayPoints());
        summary.setLocateName(requestDto.getLocateName());
        summary.setTypeCode(requestDto.getTypeCode());
        summary.setIsUsed(requestDto.getIsUsed());

        routeSummaryRepository.save(summary);

        return dto;
    }
    
    @Transactional
    public void deleteUserRoute(Long summaryId, User user) {
        RouteSummary existingSummary = routeSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new EntityNotFoundException("기존 경로 요약을 찾을 수 없습니다."));

        routeSummaryRepository.deleteById(summaryId);

        routeSummaryRepository.flush();

        user.removeSummary(existingSummary);
    }

    @Transactional(readOnly = true)
    public RouteSummaryRespDto getRouteSummaryByUserId(Long userId, Boolean isUsed) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserIdAndIsUsed(userId, isUsed)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));

        ORSResponse orsResponse = orsCilent.getORSDirection(
                summary.getStart(),
                summary.getGoal(),
                summary.getWayPoints()
        );

        return convertORSResponseToRouteSummaryRespDto(
                isUsed,
                orsResponse
        );
    }

    @Transactional(readOnly = true)
    public List<RouteGuideRespDto> getGuideByUserId(Long userId, Boolean isUsed) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserIdAndIsUsed(userId, isUsed)
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
    public List<RoutePathRespDto> getPathByUserId(Long userId, Boolean isUsed) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserIdAndIsUsed(userId, isUsed)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));

        ORSResponse orsResponse = orsCilent.getORSDirection(
                summary.getStart(),
                summary.getGoal(),
                summary.getWayPoints()
        );

        return convertToRoutePaths(orsResponse);
    }

    @Transactional(readOnly = true)
    public List<RouteLocationNameRespDto> getLocationNameByUserId(Long userId, Boolean isUsed) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserIdAndIsUsed(userId, isUsed)
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
            Boolean isUsed,
            ORSResponse orsResponse) {

        List<ORSResponse.ORSFeatures> features = orsResponse.getFeatures();

        return RouteSummaryRespDto.builder()
                .duration(features.get(0).getProperties().getSummary().getDuration())
                .distance(features.get(0).getProperties().getSummary().getDistance())
                .isUsed(isUsed)
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

                if (step.getInstruction().contains("Arrive at")) {
                    // 마지막 인덱스면 목적지
                    if (currentIndex == totalSteps - 1) {
                        if (step.getInstruction().contains("right")) {
                            instructions = "목적지가 오른쪽에 있습니다.";
                        } else if (step.getInstruction().contains("left")) {
                            instructions = "목적지가 왼쪽에 있습니다.";
                        } else {
                            instructions = "목적지";
                        }
                        type = 10;
                        locationName = locationNames.get(locationNames.size() - 1);
                    }
                    // 그 외는 경유지
                    else {
                        if (step.getInstruction().contains("right")) {
                            instructions = "경유지가 오른쪽에 있습니다.";
                        } else if (step.getInstruction().contains("left")) {
                            instructions = "경유지가 왼쪽에 있습니다.";
                        } else {
                            instructions = "경유지";
                        }
                        type = 9;
                        locationName = locationNames.get(locationNameIndex++);
                    }

                } else {
                    instructions = step.getInstruction();
                    locationName = "-".equals(step.getName()) ? "" : step.getName();
                    type = step.getType() == 11 ? 6 : step.getType();
                }

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

    // 라이딩 코스 추천 서비스
    public List<RouteRidingRecomDto> getRidingRecommend(int pageNum) {
        /*
        1. pageNum으로 RidingCourseResponse 받기
        2. RidingCourseResponse에서 출발지, 도착지, 코스명, 소요시간, 분을 설명을 리턴해줌
         */
        // 1. API 호출
        RidingCourseResponse response = ridingCourseClient.getRidingCourse(pageNum);

        // 2. 응답이 null이거나 data가 비어있을 경우 처리
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }

        // 3. 변환
        return response.getData().stream()
                .map(d -> RouteRidingRecomDto.builder()
                        .arrival(d.getArrival())
                        .description(d.getDescription())
                        .minutes(d.getMinutes())
                        .hours(d.getHours())
                        .departure(d.getDeparture())
                        .courseType(d.getCourseType())
                        .courseName(d.getCourseName())
                        .build())
                .collect(Collectors.toList());
    }

    public RouteSummaryRespDto getRouteByName(RouteByNameReqDto routeByNameReqDto) {
        // 시작, 도착지 이름으로 카카오 api를 통해서 좌표 받기
        String start = routeByNameReqDto.getStart();
        String goal = routeByNameReqDto.getGoal();
        KakaoSearchResponse kakaoSearchStart = kakaoClient.kakoSearchByName(start);
        KakaoSearchResponse kakaoSearchGoal = kakaoClient.kakoSearchByName(goal);

        // 응답 body 바탕으로 출발 도착지 경도 위도 받기
        String startLat = kakaoSearchStart.getDocuments().get(0).getY();
        String startLon = kakaoSearchStart.getDocuments().get(0).getX();
        String goalLat = kakaoSearchGoal.getDocuments().get(0).getY();
        String goalLon = kakaoSearchGoal.getDocuments().get(0).getX();

        // 출발지, 도착지 경도 위도를 바탕으로 사이에 경유지 탐색할 지점 찾기

        List<Double[]> flags = generateFlags(Double.parseDouble(startLat),Double.parseDouble(startLon),Double.parseDouble(goalLat),Double.parseDouble(goalLon));

        // 경유지 탐색지점 경도, 위도를 바탕으로 tourapi의 위치기반 관광지 조회를 호출

        StringBuilder wayPoints = new StringBuilder();
        StringBuilder wayPointNames = new StringBuilder();
        StringBuilder wayPointTypeCodes = new StringBuilder();

        // 경도 위도를 나눈점을 기준으로 해당 좌표 주변의 관광지중 하나씩을 골라 좌표를 저장
        for (Double[] flag : flags) {
            SearchLocationDto searchLocationDto = SearchLocationDto.builder()
                    .pageNum(1)
                    .mapX(String.valueOf(flag[1]))
                    .mapY(String.valueOf(flag[0]))
                    .radius("20000")
                    .typeCode("A01")
                    .build();

            // 결과 리스트가 null/empty인지 먼저 확인 (빈 리스트에서 get(0) 호출 방지)
            List<SearchAreaRespDto> results = tourApiService.searchByLocation(searchLocationDto);
            if (results == null || results.isEmpty()) {
                continue; // 이 분할 지점에서는 추가할 경유지가 없음
            }

            int randomNum = new Random().nextInt(10);
            SearchAreaRespDto searchAreaRespDto = results.get(randomNum);
            String geoCode = searchAreaRespDto.getMapx() + "," + searchAreaRespDto.getMapy();

            // WayPoint '|' 로 구분
            if (!wayPoints.isEmpty()) {
                wayPoints.append("|");
            }
            wayPoints.append(geoCode);
            wayPointNames.append(searchAreaRespDto.getTitle()).append(",");
            wayPointTypeCodes.append("경유지,");
            System.out.println(wayPoints);
        }
        // 지역이름 이름 ',' 로 분리
        String typeCodesStr = "출발지,+"+ wayPointTypeCodes +"도착지";
        if (typeCodesStr.endsWith(",")) {
            typeCodesStr = typeCodesStr.substring(0, typeCodesStr.length() - 1);
        }

        String locateNameStr = (!wayPointNames.isEmpty())
                ? routeByNameReqDto.getStart() + "," + wayPointNames + routeByNameReqDto.getGoal()
                : routeByNameReqDto.getStart() + "," + routeByNameReqDto.getGoal();

        // 출발 도착지 경도, 위도를 통해서 길찾기 호출하기
        RouteRequestDto routeRequestDto = RouteRequestDto.builder()
                .userId(routeByNameReqDto.getUserId())
                // Kakao: x=lon, y=lat → ORS lon,lat
                .start(startLon + "," + startLat)
                .goal(goalLon + "," + goalLat)
                .wayPoints(wayPoints.toString())
                .locateName(locateNameStr)
                .typeCode(typeCodesStr)
                .isUsed(false)
                .build();

        return getRoute(routeRequestDto);
    }

    private List<Double[]> generateFlags(double lat1, double lon1, double lat2, double lon2) {
        List<Double[]> waypoints = new ArrayList<>();

        double distance = haversine(lat1, lon1, lat2, lon2); // km 단위 거리

        int numPoints = (int) Math.ceil(distance / 20.0);

        if (numPoints < 2) numPoints = 2;
        if (numPoints > 5) {
            if (distance / 6.0 > 20) {
                numPoints = 6; // 강제로 6등분
            } else {
                numPoints = 5;
            }
        }

        for (int i = 1; i < numPoints; i++) {
            double ratio = (double) i / numPoints;
            double lat = lat1 + (lat2 - lat1) * ratio;
            double lon = lon1 + (lon2 - lon1) * ratio;
            waypoints.add(new Double[]{lat, lon});
        }

        return waypoints;
    }

    // 두 좌표 사이 거리(km) 계산 (Haversine 공식)
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // 지구 반지름 (km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
