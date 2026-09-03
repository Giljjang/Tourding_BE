package com.example.tourding.direction.service;

import com.example.tourding.ai.dto.AiRouteRecommendationIntentDto;
import com.example.tourding.ai.service.AiRouteRecommendationIntentService;
import com.example.tourding.ai.service.RouteScoringService;
import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.entity.RouteSummary;
import com.example.tourding.direction.entity.RouteSummaryHistory;
import com.example.tourding.direction.repository.RouteSummaryHistoryRepository;
import com.example.tourding.direction.repository.RouteSummaryRepository;
import com.example.tourding.external.kakao.KakaoClient;
import com.example.tourding.external.kakao.KakaoSearchResponse;
import com.example.tourding.external.open_routes_service.ORSCilent;
import com.example.tourding.external.open_routes_service.ORSJsonResponse;
import com.example.tourding.external.open_routes_service.ORSResponse;
import com.example.tourding.external.riding_course.RidingCourseClient;
import com.example.tourding.external.riding_course.RidingCourseResponse;
import com.example.tourding.enums.ErrorCode;
import com.example.tourding.exception.CustomException;
import com.example.tourding.tourApi.dto.SearchAreaRespDto;
import com.example.tourding.tourApi.dto.SearchLocationDto;
import com.example.tourding.tourApi.service.TourApiService;
import com.example.tourding.user.entity.User;
import com.example.tourding.user.entity.UserRidingProfile;
import com.example.tourding.user.repository.UserRepository;
import com.example.tourding.user.repository.UserRidingProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
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
    private final RouteSummaryHistoryRepository routeSummaryHistoryRepository;
    private final UserRidingProfileRepository userRidingProfileRepository;
    private final RouteScoringService routeScoringService;
    private final AiRouteRecommendationIntentService aiRouteRecommendationIntentService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public RouteGuideRespDto getRoute(RouteRequestDto requestDto) {
        User user = userRepository.findById(requestDto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));
        RouteOptionDto routeOption = resolveRouteOption(user.getId(), requestDto.getRouteOption());

        RouteBuildResult result = buildRouteResponse(requestDto, routeOption, 0.0, true);
        RouteSummary summary = saveRouteSummary(user, requestDto, routeOption, result);
        result.response().setRouteSummaryId(summary.getId());
        return result.response();
    }

    @Transactional
    public RouteRecommendationsRespDto getRouteRecommendations(RouteRecommendationReqDto requestDto) {
        RecommendationDirective directive = recommendationDirective(requestDto);
        return getRouteRecommendations(toRouteRequestDto(requestDto, directive), directive);
    }

    private RouteRecommendationsRespDto getRouteRecommendations(RouteRequestDto requestDto) {
        return getRouteRecommendations(requestDto, RecommendationDirective.empty());
    }

    private RouteRecommendationsRespDto getRouteRecommendations(RouteRequestDto requestDto, RecommendationDirective directive) {
        User user = userRepository.findById(requestDto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));
        RouteOptionDto baseOption = applyDirectiveToOption(resolveRouteOption(user.getId(), requestDto.getRouteOption()), directive);

        List<RouteCandidateDraft> drafts = candidateDrafts(requestDto, baseOption, directive);

        List<RouteBuildResult> results = drafts.stream()
                .map(draft -> CompletableFuture.supplyAsync(
                        () -> buildRouteResponse(draft.requestDto(), draft.option(), null, true, directive)
                ))
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        results.sort(Comparator.<RouteBuildResult>comparingDouble(result -> result.response().getPreferenceScore()).reversed());
        if (!results.isEmpty()) {
            RouteBuildResult best = results.get(0);
            RouteSummary saved = saveRouteSummary(user, best.requestDto(), best.option(), best);
            best.response().setRouteSummaryId(saved.getId());
        }

        return RouteRecommendationsRespDto.builder()
                .routes(results.stream().map(RouteBuildResult::response).collect(Collectors.toList()))
                .build();
    }

    private RouteRequestDto toRouteRequestDto(RouteRecommendationReqDto requestDto, RecommendationDirective directive) {
        return RouteRequestDto.builder()
                .userId(requestDto.getUserId())
                .start(requestDto.getStart())
                .goal(requestDto.getGoal())
                .wayPoints(mergedWayPoints("", directive.wayPoints()))
                .locateName(locateNameForRecommendation(directive.wayPointNames()))
                .typeCode(typeCodeForRecommendation(directive.wayPointNames()))
                .isUsed(requestDto.getIsUsed())
                .userIntentText(requestDto.getUserIntentText())
                .maxDistanceKm(directive.maxDistanceKm())
                .routeOption(requestDto.getRouteOption())
                .build();
    }

    @Transactional
    public RouteGuideRespDto rebuildRouteFromCurrentLocation(
            Long userId,
            RouteSummary routeSummary,
            Double currentLon,
            Double currentLat,
            RouteOptionDto overrideOption
    ) {
        RouteRequestDto previousRouteRequestDto = currentLocationRouteRequest(
                userId,
                routeSummary,
                currentLon,
                currentLat,
                routeOptionFromSummary(routeSummary)
        );
        RouteBuildResult previousRoute = buildRouteResponse(
                previousRouteRequestDto,
                previousRouteRequestDto.getRouteOption(),
                routeSummary.getPreferenceScore(),
                true
        );

        snapshotRouteSummary(routeSummary, "AI_ADJUSTMENT");
        RouteOptionDto option = resolveRouteOption(userId, overrideOption);
        RouteRequestDto requestDto = currentLocationRouteRequest(userId, routeSummary, currentLon, currentLat, option);

        RouteRecommendationsRespDto recommendations = getRouteRecommendations(requestDto);
        if (recommendations.getRoutes() == null || recommendations.getRoutes().isEmpty()) {
            throw new IllegalStateException("후보 경로 생성에 실패했습니다.");
        }
        RouteGuideRespDto adjustedRoute = recommendations.getRoutes().get(0);
        adjustedRoute.setAdjustmentComparison(adjustmentComparison(previousRoute.response(), adjustedRoute));
        return adjustedRoute;
    }

    @Transactional
    public RouteGuideRespDto rollbackRoute(Long routeSummaryId, Long userId) {
        RouteSummary summary = routeSummaryRepository.findById(routeSummaryId)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));
        if (!summary.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("사용자의 경로가 아닙니다.");
        }

        RouteSummaryHistory history = routeSummaryHistoryRepository
                .findFirstByRouteSummaryIdAndUserIdAndRestoredFalseOrderByCreatedAtDesc(routeSummaryId, userId)
                .orElseThrow(() -> new EntityNotFoundException("되돌릴 이전 경로 없음"));

        restoreRouteSummary(summary, history);
        history.setRestored(true);
        history.setRestoredAt(LocalDateTime.now());
        routeSummaryHistoryRepository.save(history);
        RouteSummary restored = routeSummaryRepository.save(summary);

        RouteRequestDto requestDto = requestFromSummary(restored);
        RouteBuildResult result = buildRouteResponse(requestDto, requestDto.getRouteOption(), restored.getPreferenceScore(), true);
        result.response().setRouteSummaryId(restored.getId());
        return result.response();
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
    public RouteGuideRespDto getRouteSummaryByUserId(Long userId, Boolean isUsed) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserIdAndIsUsed(userId, isUsed)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));

        RouteRequestDto requestDto = requestFromSummary(summary);
        RouteBuildResult result = buildRouteResponse(requestDto, requestDto.getRouteOption(), summary.getPreferenceScore(), true);
        result.response().setRouteSummaryId(summary.getId());
        return result.response();
    }

    @Transactional(readOnly = true)
    public RouteGuideRespDto getGuideByUserId(Long userId, Boolean isUsed) {
        return getRouteSummaryByUserId(userId, isUsed);
    }

    @Transactional(readOnly = true)
    public List<RoutePathRespDto> getPathByUserId(Long userId, Boolean isUsed) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserIdAndIsUsed(userId, isUsed)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));

        ORSResponse orsResponse = orsCilent.getORSDirection(
                summary.getStart(),
                summary.getGoal(),
                summary.getWayPoints(),
                routeOptionFromSummary(summary)
        );

        return convertToRoutePaths(orsResponse);
    }

    @Transactional(readOnly = true)
    public List<RouteLocationNameRespDto> getLocationNameByUserId(Long userId, Boolean isUsed) {
        RouteSummary summary = routeSummaryRepository.findRouteSummaryByUserIdAndIsUsed(userId, isUsed)
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));

        return convertToLocationNames(
                splitCsv(summary.getLocateName()),
                parseLocation(summary.getStart(), summary.getGoal(), summary.getWayPoints()),
                splitCsv(summary.getTypeCode()),
                splitCsv(summary.getContentId()),
                splitCsv(summary.getContentTypeId())
        );
    }

    public List<RouteRidingRecomDto> getRidingRecommend(int pageNum) {
        RidingCourseResponse response = ridingCourseClient.getRidingCourse(pageNum);

        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }

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

    @Transactional
    public RouteGuideRespDto getRouteByName(RouteByNameReqDto routeByNameReqDto) {
        String start = routeByNameReqDto.getStart();
        String goal = routeByNameReqDto.getGoal();
        KakaoSearchResponse kakaoSearchStart = kakaoClient.kakoSearchByName(start);
        KakaoSearchResponse kakaoSearchGoal = kakaoClient.kakoSearchByName(goal);

        String startLat = kakaoSearchStart.getDocuments().get(0).getY();
        String startLon = kakaoSearchStart.getDocuments().get(0).getX();
        String goalLat = kakaoSearchGoal.getDocuments().get(0).getY();
        String goalLon = kakaoSearchGoal.getDocuments().get(0).getX();

        List<Double[]> flags = generateFlags(
                Double.parseDouble(startLat),
                Double.parseDouble(startLon),
                Double.parseDouble(goalLat),
                Double.parseDouble(goalLon)
        );

        StringBuilder wayPoints = new StringBuilder();
        StringBuilder wayPointNames = new StringBuilder();
        StringBuilder wayPointTypeCodes = new StringBuilder();
        StringBuilder contentIds = new StringBuilder();
        StringBuilder contentTypeIds = new StringBuilder();

        for (Double[] flag : flags) {
            SearchLocationDto searchLocationDto = SearchLocationDto.builder()
                    .pageNum(1)
                    .mapX(String.valueOf(flag[1]))
                    .mapY(String.valueOf(flag[0]))
                    .radius("20000")
                    .typeCode("A01")
                    .build();

            List<SearchAreaRespDto> results = tourApiService.searchByLocation(searchLocationDto);
            if (results == null || results.isEmpty()) {
                continue;
            }

            SearchAreaRespDto searchAreaRespDto = results.get(new Random().nextInt(Math.min(10, results.size())));
            String geoCode = searchAreaRespDto.getMapx() + "," + searchAreaRespDto.getMapy();

            if (!wayPoints.isEmpty()) {
                wayPoints.append("|");
            }
            if (!contentIds.isEmpty() && !contentTypeIds.isEmpty()) {
                contentIds.append(",");
                contentTypeIds.append(",");
            }

            contentIds.append(defaultString(searchAreaRespDto.getContentid(), ""));
            contentTypeIds.append(defaultString(searchAreaRespDto.getContenttypeid(), ""));
            wayPoints.append(geoCode);
            wayPointNames.append(searchAreaRespDto.getTitle()).append(",");
            wayPointTypeCodes.append("경유지,");
        }

        String typeCodesStr = "출발지," + wayPointTypeCodes + "도착지";
        String locateNameStr = !wayPointNames.isEmpty()
                ? routeByNameReqDto.getStart() + "," + wayPointNames + routeByNameReqDto.getGoal()
                : routeByNameReqDto.getStart() + "," + routeByNameReqDto.getGoal();

        RouteRequestDto routeRequestDto = RouteRequestDto.builder()
                .userId(routeByNameReqDto.getUserId())
                .start(startLon + "," + startLat)
                .goal(goalLon + "," + goalLat)
                .wayPoints(wayPoints.toString())
                .locateName(locateNameStr)
                .contentId(contentIds.toString())
                .contentTypeId(contentTypeIds.toString())
                .typeCode(typeCodesStr)
                .isUsed(defaultBoolean(routeByNameReqDto.getIsUsed(), false))
                .routeOption(routeByNameReqDto.getRouteOption())
                .build();

        RouteRecommendationsRespDto recommendations = getRouteRecommendations(routeRequestDto);
        if (recommendations.getRoutes() == null || recommendations.getRoutes().isEmpty()) {
            return getRoute(routeRequestDto);
        }
        return recommendations.getRoutes().get(0);
    }

    private RouteBuildResult buildRouteResponse(RouteRequestDto requestDto, RouteOptionDto option, Double fixedScore, boolean includeAnalysis) {
        return buildRouteResponse(requestDto, option, fixedScore, includeAnalysis, RecommendationDirective.empty());
    }

    private RouteBuildResult buildRouteResponse(
            RouteRequestDto requestDto,
            RouteOptionDto option,
            Double fixedScore,
            boolean includeAnalysis,
            RecommendationDirective directive
    ) {
        RouteOptionDto resolvedOption = normalizeOption(option);
        ORSResponse orsResponse = orsCilent.getORSDirection(
                requestDto.getStart(),
                requestDto.getGoal(),
                requestDto.getWayPoints(),
                resolvedOption
        );

        ORSResponse.ORSFeatures feature = orsResponse.getFeatures().get(0);
        ORSResponse.ORSSummary summary = feature.getProperties().getSummary();
        ORSJsonResponse.Route analysisRoute = includeAnalysis ? analysisRouteFromFeature(feature) : null;

        double score = fixedScore == null && analysisRoute != null
                ? adjustedScore(routeScoringService.score(analysisRoute, weightsFor(resolvedOption, directive)).total(),
                analysisRoute,
                summary.getDistance(),
                directive)
                : defaultDouble(fixedScore, 0.0);

        List<String> locationNames = splitCsv(effectiveLocateName(requestDto));
        String[][] locationCodes = parseLocation(requestDto.getStart(), requestDto.getGoal(), requestDto.getWayPoints());
        List<String> typeCodes = splitCsv(requestDto.getTypeCode());

        RouteGuideRespDto response = RouteGuideRespDto.builder()
                .isUsed(defaultBoolean(requestDto.getIsUsed(), false))
                .distance(summary.getDistance())
                .duration(summary.getDuration())
                .ascent(analysisRoute == null || analysisRoute.getSummary() == null ? 0.0 : analysisRoute.getSummary().getAscent())
                .descent(analysisRoute == null || analysisRoute.getSummary() == null ? 0.0 : analysisRoute.getSummary().getDescent())
                .uphillLevel(uphillLevel(analysisRoute))
                .difficultyLevel(difficultyLevel(analysisRoute, summary.getDistance()))
                .surfaceSummary(surfaceSummary(analysisRoute))
                .hasConstruction(hasExtraValue(analysisRoute, "waytype", 10))
                .hasSteps(hasExtraValue(analysisRoute, "waytype", 8))
                .hasIce(hasExtraValue(analysisRoute, "surface", 13))
                .preferenceScore(score)
                .appliedOption(resolvedOption)
                .guides(convertToRouteGuides(orsResponse, locationNames, locationCodes))
                .paths(convertToRoutePaths(orsResponse))
                .locations(convertToLocationNames(
                        locationNames,
                        locationCodes,
                        typeCodes,
                        splitCsv(requestDto.getContentId()),
                        splitCsv(requestDto.getContentTypeId())
                ))
                .extraInfo(extraInfoMap(analysisRoute))
                .build();

        return new RouteBuildResult(requestDto, resolvedOption, response, analysisRoute);
    }

    private RouteSummary saveRouteSummary(User user, RouteRequestDto requestDto, RouteOptionDto option, RouteBuildResult result) {
        Boolean isUsed = defaultBoolean(requestDto.getIsUsed(), false);
        RouteSummary summary = routeSummaryRepository
                .findRouteSummaryByUserIdAndIsUsed(user.getId(), isUsed)
                .orElse(new RouteSummary());

        summary.setUser(user);
        summary.setStart(requestDto.getStart());
        summary.setGoal(requestDto.getGoal());
        summary.setWayPoints(defaultString(requestDto.getWayPoints(), ""));
        summary.setLocateName(effectiveLocateName(requestDto));
        summary.setTypeCode(defaultString(requestDto.getTypeCode(), ""));
        summary.setContentId(defaultString(requestDto.getContentId(), ""));
        summary.setContentTypeId(defaultString(requestDto.getContentTypeId(), ""));
        summary.setIsUsed(isUsed);
        summary.setCyclingProfile(option.getCyclingProfile());
        summary.setFastRoute(option.getFastRoute());
        summary.setAvoidSteps(option.getAvoidSteps());
        summary.setAvoidFords(option.getAvoidFords());
        summary.setSkillLevel(option.getSkillLevel());
        summary.setPreferenceScore(result.response().getPreferenceScore());
        summary.setExtraInfoJson(toJson(result.response().getExtraInfo()));
        summary.setRouteGeometryJson(toJson(result.response().getPaths()));

        return routeSummaryRepository.save(summary);
    }

    private void snapshotRouteSummary(RouteSummary summary, String source) {
        routeSummaryHistoryRepository.save(RouteSummaryHistory.builder()
                .user(summary.getUser())
                .routeSummary(summary)
                .source(source)
                .start(summary.getStart())
                .goal(summary.getGoal())
                .wayPoints(summary.getWayPoints())
                .typeCode(summary.getTypeCode())
                .contentId(summary.getContentId())
                .contentTypeId(summary.getContentTypeId())
                .locateName(summary.getLocateName())
                .isUsed(summary.getIsUsed())
                .cyclingProfile(summary.getCyclingProfile())
                .fastRoute(summary.getFastRoute())
                .avoidSteps(summary.getAvoidSteps())
                .avoidFords(summary.getAvoidFords())
                .skillLevel(summary.getSkillLevel())
                .preferenceScore(summary.getPreferenceScore())
                .extraInfoJson(summary.getExtraInfoJson())
                .routeGeometryJson(summary.getRouteGeometryJson())
                .restored(false)
                .build());
    }

    private void restoreRouteSummary(RouteSummary summary, RouteSummaryHistory history) {
        summary.setStart(history.getStart());
        summary.setGoal(history.getGoal());
        summary.setWayPoints(defaultString(history.getWayPoints(), ""));
        summary.setTypeCode(defaultString(history.getTypeCode(), ""));
        summary.setContentId(defaultString(history.getContentId(), ""));
        summary.setContentTypeId(defaultString(history.getContentTypeId(), ""));
        summary.setLocateName(defaultString(history.getLocateName(), "출발지,도착지"));
        summary.setIsUsed(defaultBoolean(history.getIsUsed(), true));
        summary.setCyclingProfile(history.getCyclingProfile());
        summary.setFastRoute(history.getFastRoute());
        summary.setAvoidSteps(history.getAvoidSteps());
        summary.setAvoidFords(history.getAvoidFords());
        summary.setSkillLevel(history.getSkillLevel());
        summary.setPreferenceScore(history.getPreferenceScore());
        summary.setExtraInfoJson(history.getExtraInfoJson());
        summary.setRouteGeometryJson(history.getRouteGeometryJson());
    }

    private ORSJsonResponse.Route analysisRouteFromFeature(ORSResponse.ORSFeatures feature) {
        ORSJsonResponse.Route route = new ORSJsonResponse.Route();
        ORSJsonResponse.Summary routeSummary = new ORSJsonResponse.Summary();
        ORSResponse.ORSSummary summary = feature.getProperties().getSummary();
        routeSummary.setDistance(summary.getDistance());
        routeSummary.setDuration(summary.getDuration());
        routeSummary.setAscent(summary.getAscent());
        routeSummary.setDescent(summary.getDescent());
        route.setSummary(routeSummary);
        route.setExtras(feature.getProperties().getExtras());
        List<ORSResponse.ORSSegment> segments = feature.getProperties().getSegments() == null
                ? Collections.emptyList()
                : feature.getProperties().getSegments();
        route.setSegments(segments.stream()
                .map(this::analysisSegmentFromGeoJson)
                .collect(Collectors.toList()));
        return route;
    }

    private ORSJsonResponse.Segment analysisSegmentFromGeoJson(ORSResponse.ORSSegment segment) {
        ORSJsonResponse.Segment result = new ORSJsonResponse.Segment();
        result.setDistance(segment.getDistance());
        result.setDuration(segment.getDuration());
        result.setDetourfactor(segment.getDetourfactor());
        result.setPercentage(segment.getPercentage());
        result.setAvgspeed(segment.getAvgspeed());
        result.setAscent(segment.getAscent());
        result.setDescent(segment.getDescent());
        return result;
    }

    private String[][] parseLocation(String start, String goal, String wayPoints) {
        List<String[]> locationCodes = new ArrayList<>();
        locationCodes.add(start.split(","));
        if (wayPoints != null && !wayPoints.trim().isEmpty()) {
            for (String wayPoint : wayPoints.split("\\|")) {
                if (!wayPoint.trim().isEmpty()) {
                    locationCodes.add(wayPoint.split(","));
                }
            }
        }
        locationCodes.add(goal.split(","));
        return locationCodes.toArray(new String[0][]);
    }

    private List<RouteGuideStepDto> convertToRouteGuides(
            ORSResponse orsResponse,
            List<String> locationNames,
            String[][] locationCodes
    ) {
        List<RouteGuideStepDto> routeGuides = new ArrayList<>();
        ORSResponse.ORSFeatures firstFeature = orsResponse.getFeatures().get(0);
        List<List<Double>> coordinates = firstFeature.getGeometry().getCoordinates();
        List<ORSResponse.ORSSegment> segments = firstFeature.getProperties().getSegments();

        routeGuides.add(RouteGuideStepDto.builder()
                .sequenceNum(0)
                .distance(0)
                .duration(0)
                .instructions("출발지")
                .locationName(safeGet(locationNames, 0, "출발지"))
                .pointIndex(0)
                .type(11)
                .lon(safeCoordinate(locationCodes, 0, 0))
                .lat(safeCoordinate(locationCodes, 0, 1))
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
                    if (currentIndex == totalSteps - 1) {
                        if (step.getInstruction().contains("right")) {
                            instructions = "목적지가 오른쪽에 있습니다.";
                        } else if (step.getInstruction().contains("left")) {
                            instructions = "목적지가 왼쪽에 있습니다.";
                        } else {
                            instructions = "목적지";
                        }
                        type = 10;
                        locationName = safeGet(locationNames, locationNames.size() - 1, "도착지");
                    } else {
                        if (step.getInstruction().contains("right")) {
                            instructions = "경유지가 오른쪽에 있습니다.";
                        } else if (step.getInstruction().contains("left")) {
                            instructions = "경유지가 왼쪽에 있습니다.";
                        } else {
                            instructions = "경유지";
                        }
                        type = 9;
                        locationName = safeGet(locationNames, locationNameIndex++, "경유지");
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

                int pointIndex = step.getWay_points().get(0);
                routeGuides.add(RouteGuideStepDto.builder()
                        .sequenceNum(seq++)
                        .distance((int) step.getDistance())
                        .duration((int) (step.getDuration() * 1000))
                        .instructions(instructions)
                        .locationName(locationName)
                        .pointIndex(pointIndex)
                        .type(type)
                        .lon(String.valueOf(coordinates.get(pointIndex).get(0)))
                        .lat(String.valueOf(coordinates.get(pointIndex).get(1)))
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
            List<String> typeCodes,
            List<String> contentId,
            List<String> contentTypeId
    ) {
        return IntStream.range(0, locationNames.size())
                .mapToObj(i -> RouteLocationNameRespDto.builder()
                        .sequenceNum(i + 1)
                        .name(locationNames.get(i))
                        .type(i == 0 ? "Start" : (i == locationNames.size() - 1 ? "Goal" : "WayPoint"))
                        .typeCode(routeTypeCode(typeCodes, i, locationNames.size()))
                        .contentId(i == 0 || i == locationNames.size() - 1 ? "" : safeGet(contentId, i - 1, ""))
                        .contentTypeId(i == 0 || i == locationNames.size() - 1 ? "" : safeGet(contentTypeId, i - 1, ""))
                        .lon(safeCoordinate(locationCodes, i, 0))
                        .lat(safeCoordinate(locationCodes, i, 1))
                        .build()
                ).collect(Collectors.toList());
    }

    private RouteRequestDto requestFromSummary(RouteSummary summary) {
        return RouteRequestDto.builder()
                .userId(summary.getUser().getId())
                .start(summary.getStart())
                .goal(summary.getGoal())
                .wayPoints(summary.getWayPoints())
                .locateName(summary.getLocateName())
                .typeCode(summary.getTypeCode())
                .contentId(summary.getContentId())
                .contentTypeId(summary.getContentTypeId())
                .isUsed(summary.getIsUsed())
                .routeOption(routeOptionFromSummary(summary))
                .build();
    }

    private RouteRequestDto currentLocationRouteRequest(
            Long userId,
            RouteSummary routeSummary,
            Double currentLon,
            Double currentLat,
            RouteOptionDto option
    ) {
        return RouteRequestDto.builder()
                .userId(userId)
                .start(currentLon + "," + currentLat)
                .goal(routeSummary.getGoal())
                .wayPoints(routeSummary.getWayPoints())
                .locateName(rebuildLocateName(routeSummary.getLocateName()))
                .typeCode(rebuildTypeCode(routeSummary.getTypeCode()))
                .contentId(routeSummary.getContentId())
                .contentTypeId(routeSummary.getContentTypeId())
                .isUsed(true)
                .routeOption(option)
                .build();
    }

    private RouteAdjustmentComparisonDto adjustmentComparison(RouteGuideRespDto previousRoute, RouteGuideRespDto adjustedRoute) {
        return RouteAdjustmentComparisonDto.builder()
                .durationDiffMinutes(round2((defaultDouble(adjustedRoute.getDuration(), 0.0)
                        - defaultDouble(previousRoute.getDuration(), 0.0)) / 60.0))
                .distanceDiffKm(round2((defaultDouble(adjustedRoute.getDistance(), 0.0)
                        - defaultDouble(previousRoute.getDistance(), 0.0)) / 1000.0))
                .difficultyLevelDiff(defaultInteger(adjustedRoute.getDifficultyLevel(), 0)
                        - defaultInteger(previousRoute.getDifficultyLevel(), 0))
                .build();
    }

    private RouteOptionDto resolveRouteOption(Long userId, RouteOptionDto requestOption) {
        RouteOptionDto savedOption = userRidingProfileRepository.findByUserId(userId)
                .map(this::routeOptionFromProfile)
                .orElse(RouteOptionDto.defaults());
        return mergeOption(savedOption, requestOption);
    }

    private RouteOptionDto routeOptionFromProfile(UserRidingProfile profile) {
        return RouteOptionDto.builder()
                .cyclingProfile(defaultString(profile.getCyclingProfile(), "cycling-regular"))
                .fastRoute(defaultBoolean(profile.getFastRoute(), true))
                .avoidSteps(defaultBoolean(profile.getAvoidSteps(), true))
                .avoidFords(defaultBoolean(profile.getAvoidFords(), true))
                .skillLevel(defaultString(profile.getSkillLevel(), "BEGINNER"))
                .build();
    }

    private RouteOptionDto routeOptionFromSummary(RouteSummary summary) {
        return normalizeOption(RouteOptionDto.builder()
                .cyclingProfile(summary.getCyclingProfile())
                .fastRoute(summary.getFastRoute())
                .avoidSteps(summary.getAvoidSteps())
                .avoidFords(summary.getAvoidFords())
                .skillLevel(summary.getSkillLevel())
                .build());
    }

    private RecommendationDirective recommendationDirective(RouteRecommendationReqDto requestDto) {
        AiRouteRecommendationIntentDto intent = aiRouteRecommendationIntentService.classify(requestDto.getUserIntentText());
        if (requestDto.getUserIntentText() != null
                && !requestDto.getUserIntentText().isBlank()
                && !intent.isSupported()) {
            throw new CustomException(ErrorCode.AI_RECOMMENDATION_UNSUPPORTED_INTENT);
        }

        Double maxDistanceKm = intent.getMaxDistanceKm() == null ? requestDto.getMaxDistanceKm() : intent.getMaxDistanceKm();
        if (maxDistanceKm != null && maxDistanceKm <= 0) {
            throw new CustomException(ErrorCode.AI_RECOMMENDATION_INVALID_DISTANCE_LIMIT);
        }

        List<String> wayPointNames = intent.getWaypointNames() == null ? List.of() : intent.getWaypointNames();
        List<String> wayPoints = geocodeWayPointNames(wayPointNames);

        return new RecommendationDirective(
                maxDistanceKm,
                intent.getTargetDifficulty(),
                Boolean.TRUE.equals(intent.getAvoidConstruction()),
                Boolean.TRUE.equals(intent.getAvoidSteps()),
                Boolean.TRUE.equals(intent.getAvoidIce()),
                intent.getWeightUpdate(),
                wayPoints,
                wayPointNames
        );
    }

    private List<String> geocodeWayPointNames(List<String> wayPointNames) {
        if (wayPointNames == null || wayPointNames.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String name : wayPointNames) {
            KakaoSearchResponse response = kakaoClient.kakoSearchByName(name);
            if (response == null || response.getDocuments() == null || response.getDocuments().isEmpty()) {
                throw new CustomException(ErrorCode.AI_RECOMMENDATION_WAYPOINT_NOT_FOUND);
            }
            KakaoSearchResponse.Document document = response.getDocuments().get(0);
            result.add(document.getX() + "," + document.getY());
        }
        return result;
    }

    private RouteOptionDto applyDirectiveToOption(RouteOptionDto option, RecommendationDirective directive) {
        RouteOptionDto normalized = normalizeOption(option);
        String skillLevel = directive.targetDifficulty() == null
                ? normalized.getSkillLevel()
                : skillLevelForDifficulty(directive.targetDifficulty());
        return RouteOptionDto.builder()
                .cyclingProfile(normalized.getCyclingProfile())
                .fastRoute(normalized.getFastRoute())
                .avoidSteps(directive.avoidSteps() || Boolean.TRUE.equals(normalized.getAvoidSteps()))
                .avoidFords(normalized.getAvoidFords())
                .skillLevel(skillLevel)
                .build();
    }

    private String skillLevelForDifficulty(Integer difficulty) {
        return switch (difficulty == null ? 1 : difficulty) {
            case 2 -> "NORMAL";
            case 3 -> "ADVANCED";
            case 4 -> "PRO";
            default -> "BEGINNER";
        };
    }

    private List<RouteCandidateDraft> candidateDrafts(
            RouteRequestDto requestDto,
            RouteOptionDto baseOption,
            RecommendationDirective directive
    ) {
        return List.of(
                new RouteCandidateDraft(requestDto, baseOption),
                new RouteCandidateDraft(requestDto, conservativeOption(baseOption)),
                new RouteCandidateDraft(requestDto, fastOption(baseOption))
        );
    }

    private String mergedWayPoints(String baseWayPoints, List<String> additionalWayPoints) {
        List<String> points = new ArrayList<>();
        if (baseWayPoints != null && !baseWayPoints.isBlank()) {
            points.addAll(Arrays.stream(baseWayPoints.split("\\|"))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList());
        }
        if (additionalWayPoints != null) {
            points.addAll(additionalWayPoints);
        }
        return String.join("|", points);
    }

    private String locateNameForRecommendation(List<String> wayPointNames) {
        if (wayPointNames == null || wayPointNames.isEmpty()) {
            return "출발지,도착지";
        }
        return "출발지," + String.join(",", wayPointNames) + ",도착지";
    }

    private String typeCodeForRecommendation(List<String> wayPointNames) {
        if (wayPointNames == null || wayPointNames.isEmpty()) {
            return "출발지,도착지";
        }
        return "출발지," + wayPointNames.stream().map(name -> "경유지").collect(Collectors.joining(",")) + ",도착지";
    }

    private RouteOptionDto mergeOption(RouteOptionDto base, RouteOptionDto override) {
        RouteOptionDto defaults = normalizeOption(base);
        if (override == null) {
            return defaults;
        }
        return RouteOptionDto.builder()
                .cyclingProfile(defaultString(override.getCyclingProfile(), defaults.getCyclingProfile()))
                .fastRoute(defaultBoolean(override.getFastRoute(), defaults.getFastRoute()))
                .avoidSteps(defaultBoolean(override.getAvoidSteps(), defaults.getAvoidSteps()))
                .avoidFords(defaultBoolean(override.getAvoidFords(), defaults.getAvoidFords()))
                .skillLevel(defaultString(override.getSkillLevel(), defaults.getSkillLevel()))
                .build();
    }

    private RouteOptionDto normalizeOption(RouteOptionDto option) {
        RouteOptionDto defaults = RouteOptionDto.defaults();
        if (option == null) {
            return defaults;
        }
        return RouteOptionDto.builder()
                .cyclingProfile(defaultString(option.getCyclingProfile(), defaults.getCyclingProfile()))
                .fastRoute(defaultBoolean(option.getFastRoute(), defaults.getFastRoute()))
                .avoidSteps(defaultBoolean(option.getAvoidSteps(), defaults.getAvoidSteps()))
                .avoidFords(defaultBoolean(option.getAvoidFords(), defaults.getAvoidFords()))
                .skillLevel(defaultString(option.getSkillLevel(), defaults.getSkillLevel()))
                .build();
    }

    private RouteOptionDto conservativeOption(RouteOptionDto base) {
        return RouteOptionDto.builder()
                .cyclingProfile(base.getCyclingProfile())
                .fastRoute(false)
                .avoidSteps(base.getAvoidSteps())
                .avoidFords(base.getAvoidFords())
                .skillLevel(lowerSkillLevel(base.getSkillLevel()))
                .build();
    }

    private RouteOptionDto fastOption(RouteOptionDto base) {
        return RouteOptionDto.builder()
                .cyclingProfile(base.getCyclingProfile())
                .fastRoute(true)
                .avoidSteps(base.getAvoidSteps())
                .avoidFords(base.getAvoidFords())
                .skillLevel(base.getSkillLevel())
                .build();
    }

    private String lowerSkillLevel(String skillLevel) {
        return switch (defaultString(skillLevel, "BEGINNER")) {
            case "PRO" -> "ADVANCED";
            case "ADVANCED" -> "NORMAL";
            default -> "BEGINNER";
        };
    }

    private Map<String, Double> weightsFor(RouteOptionDto option) {
        return weightsFor(option, RecommendationDirective.empty());
    }

    private Map<String, Double> weightsFor(RouteOptionDto option, RecommendationDirective directive) {
        if (directive.weights() != null && !directive.weights().isEmpty()) {
            return directive.weights();
        }
        Map<String, Double> weights = new HashMap<>();
        weights.put("comfort", 0.25);
        weights.put("flatness", 0.30);
        weights.put("surface", 0.15);
        weights.put("waytype", 0.15);
        weights.put("efficiency", 0.15);

        if (Boolean.TRUE.equals(option.getFastRoute())) {
            weights.put("efficiency", 0.35);
            weights.put("flatness", 0.20);
            weights.put("comfort", 0.20);
            weights.put("surface", 0.10);
            weights.put("waytype", 0.15);
        }
        if ("BEGINNER".equals(option.getSkillLevel())) {
            weights.put("comfort", weights.get("comfort") + 0.10);
            weights.put("flatness", weights.get("flatness") + 0.10);
            weights.put("efficiency", Math.max(0.05, weights.get("efficiency") - 0.10));
        }
        if ("cycling-road".equals(option.getCyclingProfile())) {
            weights.put("surface", weights.get("surface") + 0.10);
            weights.put("efficiency", weights.get("efficiency") + 0.05);
        }
        return normalizeWeights(weights);
    }

    private double adjustedScore(
            double baseScore,
            ORSJsonResponse.Route route,
            double distance,
            RecommendationDirective directive
    ) {
        double score = baseScore;
        if (directive.targetDifficulty() != null) {
            score -= Math.abs(difficultyLevel(route, distance) - directive.targetDifficulty()) * 0.08;
        }
        if (directive.maxDistanceKm() != null) {
            double maxDistance = directive.maxDistanceKm() * 1000.0;
            if (distance > maxDistance) {
                score -= Math.min(0.40, ((distance - maxDistance) / maxDistance) * 0.50);
            }
        }
        if (directive.avoidConstruction() && hasExtraValue(route, "waytype", 10)) {
            score -= 0.35;
        }
        if (directive.avoidSteps() && hasExtraValue(route, "waytype", 8)) {
            score -= 0.35;
        }
        if (directive.avoidIce() && hasExtraValue(route, "surface", 13)) {
            score -= 0.35;
        }
        return round4(Math.max(0.0, score));
    }

    private Map<String, Double> normalizeWeights(Map<String, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return weights;
        }
        weights.replaceAll((key, value) -> Math.round((value / total) * 10_000.0) / 10_000.0);
        return weights;
    }

    private Map<String, Object> extraInfoMap(ORSJsonResponse.Route route) {
        if (route == null || route.getExtras() == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> extraInfo = new HashMap<>();
        route.getExtras().forEach(extraInfo::put);
        return extraInfo;
    }

    private String uphillLevel(ORSJsonResponse.Route route) {
        if (route == null) {
            return "LOW";
        }
        double uphill = summaryAmount(route, "steepness", value -> value > 0);
        double severe = summaryAmount(route, "steepness", value -> value >= 3);
        if (uphill >= 30.0 || severe >= 5.0) {
            return "HIGH";
        }
        if (uphill >= 15.0 || severe >= 2.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Integer difficultyLevel(ORSJsonResponse.Route route, double distance) {
        double distanceKm = distance / 1000.0;
        double uphill = summaryAmount(route, "steepness", value -> value > 0);
        double severeUphill = summaryAmount(route, "steepness", value -> value >= 3);
        double unpaved = Math.max(
                summaryAmount(route, "waytype", value -> value == 5),
                summaryAmount(route, "surface", value -> Set.of(2, 10, 11, 12, 15, 17).contains(value))
        );

        double score = distanceScore(distanceKm) * 0.25
                + uphillScore(uphill) * 0.35
                + severeUphillScore(severeUphill) * 0.25
                + unpavedScore(unpaved) * 0.15;

        if (score < 0.75) {
            return 1;
        }
        if (score < 1.50) {
            return 2;
        }
        if (score < 2.35) {
            return 3;
        }
        return 4;
    }

    private double distanceScore(double distanceKm) {
        if (distanceKm < 15) return 0;
        if (distanceKm < 35) return 1;
        if (distanceKm < 60) return 2;
        return 3;
    }

    private double uphillScore(double uphillAmount) {
        if (uphillAmount < 10) return 0;
        if (uphillAmount < 20) return 1;
        if (uphillAmount < 35) return 2;
        return 3;
    }

    private double severeUphillScore(double severeUphillAmount) {
        if (severeUphillAmount < 1) return 0;
        if (severeUphillAmount < 4) return 1;
        if (severeUphillAmount < 8) return 2;
        return 3;
    }

    private double unpavedScore(double unpavedAmount) {
        if (unpavedAmount < 5) return 0;
        if (unpavedAmount < 15) return 1;
        if (unpavedAmount < 30) return 2;
        return 3;
    }

    private List<RouteSurfaceSummaryDto> surfaceSummary(ORSJsonResponse.Route route) {
        EnumMap<RouteSurfaceType, double[]> aggregates = new EnumMap<>(RouteSurfaceType.class);
        for (RouteSurfaceType type : RouteSurfaceType.values()) {
            aggregates.put(type, new double[]{0.0, 0.0});
        }
        ORSJsonResponse.ExtraInfo waytype = route == null || route.getExtras() == null
                ? null
                : route.getExtras().get("waytype");
        if (waytype != null && waytype.getSummary() != null) {
            for (ORSJsonResponse.ExtraSummary row : waytype.getSummary()) {
                RouteSurfaceType type = routeSurfaceType((int) row.getValue());
                double[] aggregate = aggregates.get(type);
                aggregate[0] += row.getAmount();
                aggregate[1] += row.getDistance();
            }
        }
        return aggregates.entrySet().stream()
                .map(entry -> RouteSurfaceSummaryDto.builder()
                        .type(entry.getKey())
                        .percentage(round2(entry.getValue()[0]))
                        .distance(round2(entry.getValue()[1]))
                        .build())
                .sorted(Comparator.comparing(RouteSurfaceSummaryDto::getPercentage).reversed())
                .collect(Collectors.toList());
    }

    private RouteSurfaceType routeSurfaceType(int waytype) {
        return switch (waytype) {
            case 1 -> RouteSurfaceType.MAIN_ROAD;
            case 2 -> RouteSurfaceType.ROAD;
            case 3 -> RouteSurfaceType.LOCAL_STREET;
            case 4, 6, 7 -> RouteSurfaceType.PATH_OR_CYCLEWAY;
            case 5 -> RouteSurfaceType.UNPAVED;
            default -> RouteSurfaceType.ETC;
        };
    }

    private boolean hasExtraValue(ORSJsonResponse.Route route, String key, int value) {
        return summaryAmount(route, key, current -> current == value) > 0.0;
    }

    private double summaryAmount(ORSJsonResponse.Route route, String key, IntPredicate predicate) {
        ORSJsonResponse.ExtraInfo extra = route == null || route.getExtras() == null ? null : route.getExtras().get(key);
        if (extra == null || extra.getSummary() == null) {
            return 0.0;
        }
        return extra.getSummary().stream()
                .filter(row -> predicate.test((int) row.getValue()))
                .mapToDouble(ORSJsonResponse.ExtraSummary::getAmount)
                .sum();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private int steepnessDifficulty(String skillLevel) {
        return switch (defaultString(skillLevel, "BEGINNER")) {
            case "NORMAL" -> 1;
            case "ADVANCED" -> 2;
            case "PRO" -> 3;
            default -> 0;
        };
    }

    private List<List<Double>> parseWaypoints(String wayPoints) {
        if (wayPoints == null || wayPoints.isBlank()) {
            return Collections.emptyList();
        }
        List<List<Double>> result = new ArrayList<>();
        for (String waypoint : wayPoints.split("\\|")) {
            if (!waypoint.isBlank()) {
                result.add(parseCoordinate(waypoint));
            }
        }
        return result;
    }

    private List<Double> parseCoordinate(String coordinate) {
        String[] parts = coordinate.split(",");
        return List.of(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
    }

    private List<Double[]> generateFlags(double lat1, double lon1, double lat2, double lon2) {
        List<Double[]> waypoints = new ArrayList<>();
        double distance = haversine(lat1, lon1, lat2, lon2);
        int numPoints = (int) Math.ceil(distance / 20.0);

        if (numPoints < 2) numPoints = 2;
        if (numPoints > 5) {
            if (distance / 6.0 > 20) {
                numPoints = 6;
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

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private String effectiveLocateName(RouteRequestDto requestDto) {
        return defaultString(defaultString(requestDto.getLocateName(), requestDto.getLocationName()), "출발지,도착지");
    }

    private String rebuildLocateName(String locateName) {
        List<String> names = splitCsv(locateName);
        if (names.isEmpty()) {
            return "현재 위치,도착지";
        }
        if (names.size() == 1) {
            return "현재 위치," + names.get(0);
        }
        return "현재 위치," + String.join(",", names.subList(1, names.size()));
    }

    private String rebuildTypeCode(String typeCode) {
        List<String> codes = splitCsv(typeCode);
        if (codes.size() <= 1) {
            return "출발지,도착지";
        }
        return "출발지," + String.join(",", codes.subList(1, codes.size()));
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toList());
    }

    private String routeTypeCode(List<String> typeCodes, int index, int locationCount) {
        if (index == 0 || index == locationCount - 1) {
            return "";
        }
        if (typeCodes.size() == locationCount) {
            return safeGet(typeCodes, index, "경유지");
        }
        return safeGet(typeCodes, index - 1, "경유지");
    }

    private String safeCoordinate(String[][] coordinates, int row, int col) {
        if (coordinates == null || row < 0 || row >= coordinates.length || col < 0 || col >= coordinates[row].length) {
            return "";
        }
        return coordinates[row][col].trim();
    }

    private String safeGet(List<String> values, int index, String defaultValue) {
        if (values == null || index < 0 || index >= values.size()) {
            return defaultValue;
        }
        return values.get(index);
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Boolean defaultBoolean(Boolean value, Boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private double defaultDouble(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private record RouteCandidateDraft(RouteRequestDto requestDto, RouteOptionDto option) {
    }

    private record RecommendationDirective(
            Double maxDistanceKm,
            Integer targetDifficulty,
            boolean avoidConstruction,
            boolean avoidSteps,
            boolean avoidIce,
            Map<String, Double> weights,
            List<String> wayPoints,
            List<String> wayPointNames
    ) {
        private static RecommendationDirective empty() {
            return new RecommendationDirective(
                    null,
                    null,
                    false,
                    false,
                    false,
                    null,
                    List.of(),
                    List.of()
            );
        }
    }

    private interface IntPredicate {
        boolean test(int value);
    }

    private record RouteBuildResult(
            RouteRequestDto requestDto,
            RouteOptionDto option,
            RouteGuideRespDto response,
            ORSJsonResponse.Route analysisRoute
    ) {
    }
}
