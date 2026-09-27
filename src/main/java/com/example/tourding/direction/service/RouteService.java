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
import com.fasterxml.jackson.core.type.TypeReference;
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

        List<RouteBuildResult> candidates = drafts.stream()
                .map(draft -> CompletableFuture.supplyAsync(
                        () -> safeBuildRouteResponses(draft.requestDto(), draft.option(), null, true, directive, true)
                ))
                .map(CompletableFuture::join)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        List<RouteBuildResult> results = selectRecommendationResults(candidates, directive);
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
        String locationName = locationNameForRecommendation(requestDto, directive.wayPointNames());
        return RouteRequestDto.builder()
                .userId(requestDto.getUserId())
                .start(requestDto.getStart())
                .goal(requestDto.getGoal())
                .wayPoints(mergedWayPoints("", directive.wayPoints()))
                .locationName(locationName)
                .locateName(locationName)
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
        return rebuildRouteFromCurrentLocation(
                userId,
                routeSummary,
                currentLon,
                currentLat,
                overrideOption,
                List.of(),
                null
        );
    }

    @Transactional
    public RouteGuideRespDto rebuildRouteFromCurrentLocation(
            Long userId,
            RouteSummary routeSummary,
            Double currentLon,
            Double currentLat,
            RouteOptionDto overrideOption,
            List<String> waypointQueries,
            String waypointMode
    ) {
        RouteRebuildPlan previousPlan = currentLocationRoutePlan(
                userId,
                routeSummary,
                currentLon,
                currentLat,
                routeOptionFromSummary(routeSummary),
                List.of()
        );
        RouteBuildResult previousRoute = buildRouteResponse(
                previousPlan.requestDto(),
                previousPlan.requestDto().getRouteOption(),
                routeSummary.getPreferenceScore(),
                true
        );

        RouteOptionDto option = resolveRouteOption(userId, overrideOption);
        List<ResolvedWaypoint> addedWaypoints = resolveAdjustmentWaypoints(
                currentLon,
                currentLat,
                waypointQueries
        );
        RouteRebuildPlan plan = currentLocationRoutePlan(
                userId,
                routeSummary,
                currentLon,
                currentLat,
                option,
                addedWaypoints
        );

        RouteRecommendationsRespDto recommendations = getRouteRecommendations(plan.requestDto());
        if (recommendations.getRoutes() == null || recommendations.getRoutes().isEmpty()) {
            throw new IllegalStateException("후보 경로 생성에 실패했습니다.");
        }
        RouteGuideRespDto adjustedRoute = recommendations.getRoutes().get(0);
        adjustedRoute.setPaths(mergeRoutePaths(plan.routePrefix(), adjustedRoute.getPaths()));
        adjustedRoute.setLocations(plan.fullLocations());
        persistPreviewGeometry(adjustedRoute);
        adjustedRoute.setAdjustmentComparison(adjustmentComparison(previousRoute.response(), adjustedRoute));
        return adjustedRoute;
    }

    @Transactional
    public RouteGuideRespDto rollbackRoute(Long routeSummaryId, Long userId) {
        RouteSummary summary = routeSummaryRepository.findById(routeSummaryId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTE_SUMMARY_NOT_FOUND));
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
    public RouteGuideRespDto confirmAdjustedRoute(Long previewRouteSummaryId, Long userId) {
        RouteSummary preview = routeSummaryRepository.findById(previewRouteSummaryId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTE_SUMMARY_NOT_FOUND));
        if (!preview.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("사용자의 경로가 아닙니다.");
        }
        if (Boolean.TRUE.equals(preview.getIsUsed())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        RouteSummary active = routeSummaryRepository
                .findRouteSummaryByUserIdAndIsUsed(userId, true)
                .orElseGet(RouteSummary::new);
        if (active.getId() != null) {
            snapshotRouteSummary(active, "AI_ADJUSTMENT_CONFIRM");
        }

        copyRouteSummary(preview, active, true);
        RouteSummary saved = routeSummaryRepository.save(active);

        RouteRequestDto requestDto = requestFromSummary(saved);
        RouteBuildResult result = buildRouteResponse(requestDto, requestDto.getRouteOption(), saved.getPreferenceScore(), true);
        result.response().setRouteSummaryId(saved.getId());
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
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTE_SUMMARY_NOT_FOUND));

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
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTE_SUMMARY_NOT_FOUND));

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
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTE_SUMMARY_NOT_FOUND));

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
        List<RouteBuildResult> results = buildRouteResponses(requestDto, option, fixedScore, includeAnalysis, directive, false);
        if (results.isEmpty()) {
            throw new CustomException(ErrorCode.AI_ROUTE_CANDIDATE_EMPTY);
        }
        return results.get(0);
    }

    private List<RouteBuildResult> safeBuildRouteResponses(
            RouteRequestDto requestDto,
            RouteOptionDto option,
            Double fixedScore,
            boolean includeAnalysis,
            RecommendationDirective directive,
            boolean alternativeRoutes
    ) {
        try {
            return buildRouteResponses(requestDto, option, fixedScore, includeAnalysis, directive, alternativeRoutes);
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    private List<RouteBuildResult> buildRouteResponses(
            RouteRequestDto requestDto,
            RouteOptionDto option,
            Double fixedScore,
            boolean includeAnalysis,
            RecommendationDirective directive,
            boolean alternativeRoutes
    ) {
        RouteOptionDto resolvedOption = normalizeOption(option);
        ORSResponse orsResponse = orsCilent.getORSDirection(
                requestDto.getStart(),
                requestDto.getGoal(),
                requestDto.getWayPoints(),
                resolvedOption,
                alternativeRoutes
        );
        if (orsResponse == null || orsResponse.getFeatures() == null || orsResponse.getFeatures().isEmpty()) {
            return Collections.emptyList();
        }

        return orsResponse.getFeatures().stream()
                .map(feature -> buildRouteResultFromFeature(
                        requestDto,
                        resolvedOption,
                        fixedScore,
                        includeAnalysis,
                        directive,
                        feature
                ))
                .collect(Collectors.toList());
    }

    private RouteBuildResult buildRouteResultFromFeature(
            RouteRequestDto requestDto,
            RouteOptionDto resolvedOption,
            Double fixedScore,
            boolean includeAnalysis,
            RecommendationDirective directive,
            ORSResponse.ORSFeatures feature
    ) {
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
                .guides(convertToRouteGuides(feature, locationNames, locationCodes))
                .paths(convertToRoutePaths(feature))
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
        summary.setRoutePreference(option.getRoutePreference());
        summary.setFastRoute(option.getFastRoute());
        summary.setAvoidSteps(option.getAvoidSteps());
        summary.setAvoidFords(option.getAvoidFords());
        summary.setAvoidFerries(option.getAvoidFerries());
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
                .routePreference(summary.getRoutePreference())
                .fastRoute(summary.getFastRoute())
                .avoidSteps(summary.getAvoidSteps())
                .avoidFords(summary.getAvoidFords())
                .avoidFerries(summary.getAvoidFerries())
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
        summary.setRoutePreference(history.getRoutePreference());
        summary.setFastRoute(history.getFastRoute());
        summary.setAvoidSteps(history.getAvoidSteps());
        summary.setAvoidFords(history.getAvoidFords());
        summary.setAvoidFerries(history.getAvoidFerries());
        summary.setSkillLevel(history.getSkillLevel());
        summary.setPreferenceScore(history.getPreferenceScore());
        summary.setExtraInfoJson(history.getExtraInfoJson());
        summary.setRouteGeometryJson(history.getRouteGeometryJson());
    }

    private void copyRouteSummary(RouteSummary source, RouteSummary target, boolean isUsed) {
        target.setUser(source.getUser());
        target.setStart(source.getStart());
        target.setGoal(source.getGoal());
        target.setWayPoints(defaultString(source.getWayPoints(), ""));
        target.setTypeCode(defaultString(source.getTypeCode(), ""));
        target.setContentId(defaultString(source.getContentId(), ""));
        target.setContentTypeId(defaultString(source.getContentTypeId(), ""));
        target.setLocateName(defaultString(source.getLocateName(), "출발지,도착지"));
        target.setIsUsed(isUsed);
        target.setCyclingProfile(source.getCyclingProfile());
        target.setRoutePreference(source.getRoutePreference());
        target.setFastRoute(source.getFastRoute());
        target.setAvoidSteps(source.getAvoidSteps());
        target.setAvoidFords(source.getAvoidFords());
        target.setAvoidFerries(source.getAvoidFerries());
        target.setSkillLevel(source.getSkillLevel());
        target.setPreferenceScore(source.getPreferenceScore());
        target.setExtraInfoJson(source.getExtraInfoJson());
        target.setRouteGeometryJson(source.getRouteGeometryJson());
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

    private List<RouteBuildResult> selectRecommendationResults(
            List<RouteBuildResult> candidates,
            RecommendationDirective directive
    ) {
        if (candidates == null || candidates.isEmpty()) {
            throw new CustomException(ErrorCode.AI_ROUTE_CANDIDATE_EMPTY);
        }

        List<RouteBuildResult> pool = candidates.stream()
                .filter(result -> result != null && result.response() != null)
                .collect(Collectors.toCollection(ArrayList::new));
        applyIntentRankingScore(pool, directive);
        pool.sort(Comparator.<RouteBuildResult>comparingDouble(result -> result.response().getPreferenceScore()).reversed());

        List<RouteBuildResult> selected = new ArrayList<>();
        while (!pool.isEmpty() && selected.size() < 3) {
            RouteBuildResult next = pool.stream()
                    .max(Comparator.comparingDouble(result -> diversityAdjustedScore(result, selected)))
                    .orElse(pool.get(0));
            selected.add(next);
            pool.remove(next);
        }

        int index = 0;
        while (selected.size() < 3 && !candidates.isEmpty()) {
            selected.add(candidates.get(index % candidates.size()));
            index++;
        }
        return selected;
    }

    private void applyIntentRankingScore(List<RouteBuildResult> candidates, RecommendationDirective directive) {
        if (candidates.isEmpty()) {
            return;
        }

        double minDistance = candidates.stream()
                .mapToDouble(result -> defaultDouble(result.response().getDistance(), 0.0))
                .min()
                .orElse(0.0);
        double maxDistance = candidates.stream()
                .mapToDouble(result -> defaultDouble(result.response().getDistance(), 0.0))
                .max()
                .orElse(0.0);
        double minDuration = candidates.stream()
                .mapToDouble(result -> defaultDouble(result.response().getDuration(), 0.0))
                .min()
                .orElse(0.0);
        double maxDuration = candidates.stream()
                .mapToDouble(result -> defaultDouble(result.response().getDuration(), 0.0))
                .max()
                .orElse(0.0);
        double minDetour = candidates.stream()
                .mapToDouble(this::detourFactor)
                .min()
                .orElse(0.0);
        double maxDetour = candidates.stream()
                .mapToDouble(this::detourFactor)
                .max()
                .orElse(0.0);

        for (RouteBuildResult candidate : candidates) {
            double base = defaultDouble(candidate.response().getPreferenceScore(), 0.0);
            double intentScore = 0.0;
            int intentCount = 0;

            if ("FASTEST".equals(directive.routePreference())) {
                intentScore += normalizedInverse(candidate.response().getDuration(), minDuration, maxDuration);
                intentCount++;
            } else if ("SHORTEST".equals(directive.routePreference())) {
                intentScore += normalizedInverse(candidate.response().getDistance(), minDistance, maxDistance);
                intentCount++;
            }

            if ("LONGEST".equals(directive.routeShape())) {
                intentScore += normalized(candidate.response().getDistance(), minDistance, maxDistance);
                intentCount++;
            } else if ("DETOUR".equals(directive.routeShape())) {
                intentScore += normalized(detourFactor(candidate), minDetour, maxDetour);
                intentCount++;
            } else if ("DIRECT".equals(directive.routeShape())) {
                intentScore += normalizedInverse(detourFactor(candidate), minDetour, maxDetour);
                intentCount++;
            }

            double finalScore = intentCount == 0
                    ? base
                    : (base * 0.65) + ((intentScore / intentCount) * 0.35);
            candidate.response().setPreferenceScore(round4(Math.max(0.0, Math.min(1.0, finalScore))));
        }
    }

    private double detourFactor(RouteBuildResult result) {
        if (result.analysisRoute() == null || result.analysisRoute().getSegments() == null) {
            return 0.0;
        }
        return result.analysisRoute().getSegments().stream()
                .mapToDouble(segment -> segment.getDetourfactor() * (segment.getPercentage() / 100.0))
                .sum();
    }

    private double normalized(double value, double min, double max) {
        if (max <= min) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    private double normalizedInverse(double value, double min, double max) {
        return 1.0 - normalized(value, min, max);
    }

    private double diversityAdjustedScore(RouteBuildResult candidate, List<RouteBuildResult> selected) {
        double score = defaultDouble(candidate.response().getPreferenceScore(), 0.0);
        double overlap = selected.stream()
                .mapToDouble(result -> routeOverlap(candidate.response(), result.response()))
                .max()
                .orElse(0.0);
        return score - overlap * 0.25;
    }

    private double routeOverlap(RouteGuideRespDto left, RouteGuideRespDto right) {
        Set<String> leftPoints = routePointKeys(left);
        Set<String> rightPoints = routePointKeys(right);
        if (leftPoints.isEmpty() || rightPoints.isEmpty()) {
            return 0.0;
        }
        long shared = leftPoints.stream().filter(rightPoints::contains).count();
        return (double) shared / Math.min(leftPoints.size(), rightPoints.size());
    }

    private Set<String> routePointKeys(RouteGuideRespDto route) {
        if (route == null || route.getPaths() == null) {
            return Collections.emptySet();
        }
        return route.getPaths().stream()
                .filter(path -> path.getLon() != null && path.getLat() != null)
                .map(path -> quantizedCoordinate(path.getLon(), path.getLat()))
                .collect(Collectors.toSet());
    }

    private String quantizedCoordinate(String lon, String lat) {
        try {
            double parsedLon = Math.round(Double.parseDouble(lon) * 10_000.0) / 10_000.0;
            double parsedLat = Math.round(Double.parseDouble(lat) * 10_000.0) / 10_000.0;
            return parsedLon + "," + parsedLat;
        } catch (NumberFormatException e) {
            return lon + "," + lat;
        }
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
            ORSResponse.ORSFeatures firstFeature,
            List<String> locationNames,
            String[][] locationCodes
    ) {
        List<RouteGuideStepDto> routeGuides = new ArrayList<>();
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

    private List<RoutePathRespDto> convertToRoutePaths(ORSResponse.ORSFeatures feature) {
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

    private List<RoutePathRespDto> convertToRoutePaths(ORSResponse orsResponse) {
        if (orsResponse == null || orsResponse.getFeatures() == null || orsResponse.getFeatures().isEmpty()) {
            return Collections.emptyList();
        }
        return convertToRoutePaths(orsResponse.getFeatures().get(0));
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

    private RouteRebuildPlan currentLocationRoutePlan(
            Long userId,
            RouteSummary routeSummary,
            Double currentLon,
            Double currentLat,
            RouteOptionDto option,
            List<ResolvedWaypoint> addedWaypoints
    ) {
        List<RoutePathRespDto> savedPaths = parseSavedRoutePaths(routeSummary.getRouteGeometryJson());
        int currentPathIndex = nearestPathIndex(savedPaths, currentLon, currentLat);
        List<WaypointEntry> existingWaypoints = waypointEntries(routeSummary, savedPaths);
        List<WaypointEntry> remainingWaypoints = existingWaypoints.stream()
                .filter(waypoint -> waypoint.pathIndex() < 0 || waypoint.pathIndex() > currentPathIndex)
                .collect(Collectors.toCollection(ArrayList::new));

        List<WaypointEntry> requestedWaypoints = new ArrayList<>();
        for (ResolvedWaypoint added : addedWaypoints == null ? List.<ResolvedWaypoint>of() : addedWaypoints) {
            requestedWaypoints.add(new WaypointEntry(
                    added.coordinate(),
                    added.name(),
                    "경유지",
                    "",
                    "",
                    -1
            ));
        }
        requestedWaypoints.addAll(remainingWaypoints);

        String wayPoints = requestedWaypoints.stream()
                .map(WaypointEntry::coordinate)
                .collect(Collectors.joining("|"));
        List<String> locationNames = new ArrayList<>();
        locationNames.add("현재 위치");
        locationNames.addAll(requestedWaypoints.stream().map(WaypointEntry::name).toList());
        locationNames.add(lastLocationName(routeSummary.getLocateName()));

        List<String> typeCodes = new ArrayList<>();
        typeCodes.add("출발지");
        typeCodes.addAll(requestedWaypoints.stream().map(WaypointEntry::typeCode).toList());
        typeCodes.add("도착지");

        List<String> contentIds = requestedWaypoints.stream().map(WaypointEntry::contentId).toList();
        List<String> contentTypeIds = requestedWaypoints.stream().map(WaypointEntry::contentTypeId).toList();

        RouteRequestDto requestDto = RouteRequestDto.builder()
                .userId(userId)
                .start(currentLon + "," + currentLat)
                .goal(routeSummary.getGoal())
                .wayPoints(wayPoints)
                .locateName(String.join(",", locationNames))
                .typeCode(String.join(",", typeCodes))
                .contentId(String.join(",", contentIds))
                .contentTypeId(String.join(",", contentTypeIds))
                .isUsed(false)
                .routeOption(option)
                .build();

        List<RouteLocationNameRespDto> fullLocations = fullRebuildLocations(
                routeSummary,
                currentLon,
                currentLat,
                currentPathIndex,
                existingWaypoints,
                requestedWaypoints
        );
        List<RoutePathRespDto> routePrefix = currentPathIndex < 0 || savedPaths.isEmpty()
                ? List.of()
                : new ArrayList<>(savedPaths.subList(0, Math.min(currentPathIndex + 1, savedPaths.size())));
        return new RouteRebuildPlan(requestDto, routePrefix, fullLocations);
    }

    private List<ResolvedWaypoint> resolveAdjustmentWaypoints(
            Double currentLon,
            Double currentLat,
            List<String> waypointQueries
    ) {
        if (currentLon == null || currentLat == null || waypointQueries == null || waypointQueries.isEmpty()) {
            return List.of();
        }

        List<ResolvedWaypoint> resolved = new ArrayList<>();
        List<Double> previousPoint = List.of(currentLon, currentLat);
        for (String query : waypointQueries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            KakaoSearchResponse.Document document = findWaypointNearPoint(previousPoint, query).orElse(null);
            if (document == null) {
                throw new CustomException(
                        ErrorCode.AI_RECOMMENDATION_WAYPOINT_NOT_FOUND,
                        "현재 위치 주변에서 경유지 '" + query + "'을(를) 찾을 수 없습니다."
                );
            }
            String coordinate = document.getX() + "," + document.getY();
            resolved.add(new ResolvedWaypoint(coordinate, defaultString(document.getPlace_name(), query)));
            previousPoint = parseCoordinate(coordinate);
        }
        return resolved;
    }

    private List<WaypointEntry> waypointEntries(RouteSummary routeSummary, List<RoutePathRespDto> savedPaths) {
        List<String> coordinates = routeSummary.getWayPoints() == null || routeSummary.getWayPoints().isBlank()
                ? List.of()
                : Arrays.stream(routeSummary.getWayPoints().split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        List<String> names = splitCsv(routeSummary.getLocateName());
        List<String> typeCodes = splitCsv(routeSummary.getTypeCode());
        List<String> contentIds = splitCsv(routeSummary.getContentId());
        List<String> contentTypeIds = splitCsv(routeSummary.getContentTypeId());

        List<WaypointEntry> result = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i++) {
            String coordinate = coordinates.get(i);
            int pathIndex = -1;
            try {
                List<Double> parsed = parseCoordinate(coordinate);
                pathIndex = nearestPathIndex(savedPaths, parsed.get(0), parsed.get(1));
            } catch (RuntimeException ignored) {
                // Keep malformed legacy data in the remaining request.
            }
            result.add(new WaypointEntry(
                    coordinate,
                    safeGet(names, i + 1, "경유지"),
                    safeGet(typeCodes, i + 1, "경유지"),
                    safeGet(contentIds, i, ""),
                    safeGet(contentTypeIds, i, ""),
                    pathIndex
            ));
        }
        return result;
    }

    private List<RoutePathRespDto> parseSavedRoutePaths(String routeGeometryJson) {
        if (routeGeometryJson == null || routeGeometryJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(routeGeometryJson, new TypeReference<List<RoutePathRespDto>>() {
            });
        } catch (JsonProcessingException | RuntimeException e) {
            return List.of();
        }
    }

    private int nearestPathIndex(List<RoutePathRespDto> paths, Double lon, Double lat) {
        if (paths == null || paths.isEmpty() || lon == null || lat == null) {
            return -1;
        }
        double minDistance = Double.MAX_VALUE;
        int nearestIndex = -1;
        for (int i = 0; i < paths.size(); i++) {
            RoutePathRespDto path = paths.get(i);
            try {
                double pathLon = Double.parseDouble(path.getLon());
                double pathLat = Double.parseDouble(path.getLat());
                double distance = haversine(lat, lon, pathLat, pathLon);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestIndex = i;
                }
            } catch (RuntimeException ignored) {
                // Skip malformed geometry points.
            }
        }
        return nearestIndex;
    }

    private List<RoutePathRespDto> mergeRoutePaths(
            List<RoutePathRespDto> routePrefix,
            List<RoutePathRespDto> remainingPaths
    ) {
        List<RoutePathRespDto> merged = new ArrayList<>();
        if (routePrefix != null) {
            merged.addAll(routePrefix);
        }
        if (remainingPaths != null) {
            for (RoutePathRespDto path : remainingPaths) {
                if (!merged.isEmpty() && sameCoordinate(merged.get(merged.size() - 1), path)) {
                    continue;
                }
                merged.add(path);
            }
        }
        for (int i = 0; i < merged.size(); i++) {
            merged.get(i).setSequenceNum(i);
        }
        return merged;
    }

    private boolean sameCoordinate(RoutePathRespDto left, RoutePathRespDto right) {
        try {
            return haversine(
                    Double.parseDouble(left.getLat()),
                    Double.parseDouble(left.getLon()),
                    Double.parseDouble(right.getLat()),
                    Double.parseDouble(right.getLon())
            ) < 0.005;
        } catch (RuntimeException e) {
            return Objects.equals(left.getLon(), right.getLon())
                    && Objects.equals(left.getLat(), right.getLat());
        }
    }

    private void persistPreviewGeometry(RouteGuideRespDto response) {
        if (response.getRouteSummaryId() == null || response.getPaths() == null) {
            return;
        }
        routeSummaryRepository.findById(response.getRouteSummaryId()).ifPresent(summary -> {
            summary.setRouteGeometryJson(toJson(response.getPaths()));
            routeSummaryRepository.save(summary);
        });
    }

    private List<RouteLocationNameRespDto> fullRebuildLocations(
            RouteSummary routeSummary,
            Double currentLon,
            Double currentLat,
            int currentPathIndex,
            List<WaypointEntry> existingWaypoints,
            List<WaypointEntry> requestedWaypoints
    ) {
        List<RouteLocationNameRespDto> locations = new ArrayList<>();
        List<String> oldNames = splitCsv(routeSummary.getLocateName());
        locations.add(RouteLocationNameRespDto.builder()
                .name(safeGet(oldNames, 0, "출발지"))
                .type("Start")
                .typeCode("출발지")
                .lon(firstCoordinate(routeSummary.getStart(), 0))
                .lat(firstCoordinate(routeSummary.getStart(), 1))
                .build());

        for (WaypointEntry waypoint : existingWaypoints) {
            if (currentPathIndex >= 0 && waypoint.pathIndex() >= 0 && waypoint.pathIndex() <= currentPathIndex) {
                locations.add(locationFromWaypoint(waypoint, "WayPoint"));
            }
        }

        locations.add(RouteLocationNameRespDto.builder()
                .name("현재 위치")
                .type("Current")
                .typeCode("현재위치")
                .lon(String.valueOf(currentLon))
                .lat(String.valueOf(currentLat))
                .build());

        for (WaypointEntry waypoint : requestedWaypoints) {
            if (existingWaypoints.contains(waypoint)
                    && currentPathIndex >= 0
                    && waypoint.pathIndex() >= 0
                    && waypoint.pathIndex() <= currentPathIndex) {
                continue;
            }
            locations.add(locationFromWaypoint(waypoint, "WayPoint"));
        }

        locations.add(RouteLocationNameRespDto.builder()
                .name(lastLocationName(routeSummary.getLocateName()))
                .type("Goal")
                .typeCode("도착지")
                .lon(firstCoordinate(routeSummary.getGoal(), 0))
                .lat(firstCoordinate(routeSummary.getGoal(), 1))
                .build());

        List<RouteLocationNameRespDto> sequenced = new ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            RouteLocationNameRespDto location = locations.get(i);
            sequenced.add(RouteLocationNameRespDto.builder()
                    .sequenceNum(i + 1)
                    .name(location.getName())
                    .type(location.getType())
                    .typeCode(location.getTypeCode())
                    .contentId(location.getContentId())
                    .contentTypeId(location.getContentTypeId())
                    .lon(location.getLon())
                    .lat(location.getLat())
                    .build());
        }
        return sequenced;
    }

    private RouteLocationNameRespDto locationFromWaypoint(WaypointEntry waypoint, String type) {
        List<Double> coordinate = parseCoordinate(waypoint.coordinate());
        return RouteLocationNameRespDto.builder()
                .name(waypoint.name())
                .type(type)
                .typeCode(waypoint.typeCode())
                .contentId(waypoint.contentId())
                .contentTypeId(waypoint.contentTypeId())
                .lon(String.valueOf(coordinate.get(0)))
                .lat(String.valueOf(coordinate.get(1)))
                .build();
    }

    private String firstCoordinate(String coordinate, int index) {
        try {
            return String.valueOf(parseCoordinate(coordinate).get(index));
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String lastLocationName(String locateName) {
        List<String> names = splitCsv(locateName);
        return names.isEmpty() ? "도착지" : names.get(names.size() - 1);
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
                .routePreference(summary.getRoutePreference())
                .avoidSteps(summary.getAvoidSteps())
                .avoidFords(summary.getAvoidFords())
                .avoidFerries(summary.getAvoidFerries())
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

        List<String> requestedWayPointNames = intent.getWaypointNames() == null ? List.of() : intent.getWaypointNames();
        List<ResolvedWaypoint> resolvedWayPoints = geocodeWayPointNames(requestDto, requestedWayPointNames);
        List<String> wayPoints = resolvedWayPoints.stream()
                .map(ResolvedWaypoint::coordinate)
                .toList();
        List<String> wayPointNames = resolvedWayPoints.stream()
                .map(ResolvedWaypoint::name)
                .toList();

        return new RecommendationDirective(
                maxDistanceKm,
                intent.getTargetDifficulty(),
                Boolean.TRUE.equals(intent.getAvoidConstruction()),
                Boolean.TRUE.equals(intent.getAvoidSteps()),
                Boolean.TRUE.equals(intent.getAvoidFords()),
                Boolean.TRUE.equals(intent.getAvoidFerries()),
                Boolean.TRUE.equals(intent.getAvoidIce()),
                intent.getFastRoute(),
                normalizedRoutePreference(intent.getRoutePreference()),
                normalizedRouteShape(intent.getRouteShape()),
                normalizedCyclingProfile(intent.getCyclingProfile()),
                Boolean.TRUE.equals(intent.getPreferPaved()),
                Boolean.TRUE.equals(intent.getPreferBikeRoad()),
                Boolean.TRUE.equals(intent.getAvoidMainRoad())
                        || "QUIET".equalsIgnoreCase(intent.getRoadPreference())
                        || "MAIN_ROAD_AVOID".equalsIgnoreCase(intent.getRoadPreference()),
                normalizedRoadPreference(intent.getRoadPreference()),
                intent.getWeightUpdate(),
                wayPoints,
                wayPointNames
        );
    }

    private List<ResolvedWaypoint> geocodeWayPointNames(RouteRecommendationReqDto requestDto, List<String> wayPointNames) {
        if (wayPointNames == null || wayPointNames.isEmpty()) {
            return List.of();
        }
        List<Double> start = parseCoordinate(requestDto.getStart());
        List<Double> goal = parseCoordinate(requestDto.getGoal());
        Optional<List<Double>> userLocation = userLocation(requestDto);
        List<ResolvedWaypoint> result = new ArrayList<>();
        List<Double> previousPoint = start;
        for (String name : wayPointNames) {
            KakaoSearchResponse.Document document = findWaypointNearPoint(previousPoint, name)
                    .or(() -> findWaypointOnRouteLine(start, goal, name))
                    .or(() -> userLocation.flatMap(location -> findWaypointNearPoint(location, name)))
                    .orElse(null);
            if (document == null) {
                throw waypointNotFound(name);
            }
            String coordinate = document.getX() + "," + document.getY();
            result.add(new ResolvedWaypoint(
                    coordinate,
                    defaultString(document.getPlace_name(), name)
            ));
            previousPoint = parseCoordinate(coordinate);
        }
        return result;
    }

    private CustomException waypointNotFound(String name) {
        return new CustomException(
                ErrorCode.AI_RECOMMENDATION_WAYPOINT_NOT_FOUND,
                "경로상 주변에 경유지 '" + name + "'을(를) 찾을 수 없습니다."
        );
    }

    private Optional<KakaoSearchResponse.Document> findWaypointOnRouteLine(List<Double> start, List<Double> goal, String name) {
        String radius = routeWaypointSearchRadius(start, goal);

        Map<String, KakaoSearchResponse.Document> candidates = new LinkedHashMap<>();
        for (SearchPoint point : routeSearchPoints(start, goal)) {
            collectWaypointCandidates(candidates, point.lon(), point.lat(), radius, name);
        }
        return candidates.values().stream()
                .min(Comparator.comparingDouble(document -> routeDetourKm(start, goal, document)));
    }

    private Optional<KakaoSearchResponse.Document> findWaypointNearPoint(List<Double> point, String name) {
        Map<String, KakaoSearchResponse.Document> candidates = new LinkedHashMap<>();
        collectWaypointCandidates(candidates, point.get(0), point.get(1), "5000", name);
        if (candidates.isEmpty()) {
            collectWaypointCandidates(candidates, point.get(0), point.get(1), "10000", name);
        }
        return candidates.values().stream()
                .min(Comparator.comparingDouble(document -> haversine(
                        point.get(1),
                        point.get(0),
                        Double.parseDouble(document.getY()),
                        Double.parseDouble(document.getX())
                )));
    }

    private void collectWaypointCandidates(
            Map<String, KakaoSearchResponse.Document> candidates,
            double lon,
            double lat,
            String radius,
            String name
    ) {
        KakaoSearchResponse response;
        try {
            response = kakaoClient.kakaoSearchByLocation(
                    String.valueOf(lon),
                    String.valueOf(lat),
                    radius,
                    name
            );
        } catch (RuntimeException e) {
            return;
        }
        if (response == null || response.getDocuments() == null || response.getDocuments().isEmpty()) {
            return;
        }
        for (KakaoSearchResponse.Document document : response.getDocuments()) {
            if (hasCoordinate(document)) {
                candidates.putIfAbsent(kakaoDocumentKey(document), document);
            }
        }
    }

    private Optional<List<Double>> userLocation(RouteRecommendationReqDto requestDto) {
        if (requestDto.getCurrentLon() == null || requestDto.getCurrentLat() == null) {
            return Optional.empty();
        }
        return Optional.of(List.of(requestDto.getCurrentLon(), requestDto.getCurrentLat()));
    }

    private boolean hasCoordinate(KakaoSearchResponse.Document document) {
        return document != null
                && document.getX() != null
                && !document.getX().isBlank()
                && document.getY() != null
                && !document.getY().isBlank();
    }

    private String kakaoDocumentKey(KakaoSearchResponse.Document document) {
        if (document.getId() != null && !document.getId().isBlank()) {
            return document.getId();
        }
        return document.getX() + "," + document.getY();
    }

    private List<SearchPoint> routeSearchPoints(List<Double> start, List<Double> goal) {
        double startLon = start.get(0);
        double startLat = start.get(1);
        double goalLon = goal.get(0);
        double goalLat = goal.get(1);
        return List.of(
                new SearchPoint(startLon, startLat),
                interpolateSearchPoint(startLon, startLat, goalLon, goalLat, 0.15),
                interpolateSearchPoint(startLon, startLat, goalLon, goalLat, 0.25),
                interpolateSearchPoint(startLon, startLat, goalLon, goalLat, 0.50),
                interpolateSearchPoint(startLon, startLat, goalLon, goalLat, 0.75),
                interpolateSearchPoint(startLon, startLat, goalLon, goalLat, 0.85),
                new SearchPoint(goalLon, goalLat)
        );
    }

    private SearchPoint interpolateSearchPoint(double startLon, double startLat, double goalLon, double goalLat, double ratio) {
        return new SearchPoint(
                startLon + (goalLon - startLon) * ratio,
                startLat + (goalLat - startLat) * ratio
        );
    }

    private String routeWaypointSearchRadius(List<Double> start, List<Double> goal) {
        double distanceKm = haversine(start.get(1), start.get(0), goal.get(1), goal.get(0));
        if (distanceKm < 5.0) {
            return "1500";
        }
        if (distanceKm < 20.0) {
            return "5000";
        }
        if (distanceKm < 50.0) {
            return "8000";
        }
        return "10000";
    }

    private double routeDetourKm(List<Double> start, List<Double> goal, KakaoSearchResponse.Document document) {
        double waypointLon = Double.parseDouble(document.getX());
        double waypointLat = Double.parseDouble(document.getY());
        double direct = haversine(start.get(1), start.get(0), goal.get(1), goal.get(0));
        double viaWaypoint = haversine(start.get(1), start.get(0), waypointLat, waypointLon)
                + haversine(waypointLat, waypointLon, goal.get(1), goal.get(0));
        return viaWaypoint - direct;
    }

    private RouteOptionDto applyDirectiveToOption(RouteOptionDto option, RecommendationDirective directive) {
        RouteOptionDto normalized = normalizeOption(option);
        String skillLevel = directive.targetDifficulty() == null
                ? normalized.getSkillLevel()
                : skillLevelForDifficulty(directive.targetDifficulty());
        String routePreference = directive.routePreference();
        if (routePreference == null
                && ("LONGEST".equals(directive.routeShape()) || "DETOUR".equals(directive.routeShape()))) {
            routePreference = "RECOMMENDED";
        }
        Boolean fastRoute = directive.fastRoute();
        if (fastRoute == null && routePreference != null) {
            fastRoute = "FASTEST".equals(routePreference);
        }
        return RouteOptionDto.builder()
                .cyclingProfile(defaultString(directive.cyclingProfile(), normalized.getCyclingProfile()))
                .fastRoute(fastRoute == null ? normalized.getFastRoute() : fastRoute)
                .routePreference(defaultString(routePreference, normalized.getRoutePreference()))
                .avoidSteps(directive.avoidSteps() || Boolean.TRUE.equals(normalized.getAvoidSteps()))
                .avoidFords(directive.avoidFords() || Boolean.TRUE.equals(normalized.getAvoidFords()))
                .avoidFerries(directive.avoidFerries() || Boolean.TRUE.equals(normalized.getAvoidFerries()))
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
        List<RouteOptionDto> options = new ArrayList<>();
        options.add(baseOption);
        options.add(conservativeOption(baseOption));
        options.add(fastOption(baseOption));
        options.add(roadOption(baseOption, directive));
        options.add(bikeFriendlyOption(baseOption, directive));
        if ("LONGEST".equals(directive.routeShape()) || "DETOUR".equals(directive.routeShape())) {
            options.add(preferenceOption(baseOption, "RECOMMENDED"));
            options.add(preferenceOption(baseOption, "SHORTEST"));
            options.add(preferenceOption(baseOption, "FASTEST"));
        }

        Set<String> seen = new HashSet<>();
        return options.stream()
                .map(this::normalizeOption)
                .filter(option -> seen.add(optionSignature(option)))
                .map(option -> new RouteCandidateDraft(requestDto, option))
                .collect(Collectors.toList());
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

    private String locationNameForRecommendation(RouteRecommendationReqDto requestDto, List<String> wayPointNames) {
        List<String> baseNames = splitCsv(defaultString(requestDto.getLocateName(), requestDto.getLocationName()));
        String startName = resolveRecommendationEndpointName(
                baseNames.isEmpty() ? null : baseNames.get(0),
                requestDto.getStart(),
                "출발지"
        );
        String goalName = resolveRecommendationEndpointName(
                baseNames.size() >= 2 ? baseNames.get(baseNames.size() - 1) : null,
                requestDto.getGoal(),
                "도착지"
        );
        if (wayPointNames == null || wayPointNames.isEmpty()) {
            return startName + "," + goalName;
        }
        return startName + "," + String.join(",", wayPointNames) + "," + goalName;
    }

    private String resolveRecommendationEndpointName(String requestedName, String coordinate, String defaultLabel) {
        if (requestedName != null && !requestedName.isBlank() && !requestedName.equals(defaultLabel)) {
            return requestedName;
        }
        return resolveCoordinateAddressName(coordinate, defaultLabel);
    }

    private String resolveCoordinateAddressName(String coordinate, String fallback) {
        try {
            List<Double> parsed = parseCoordinate(coordinate);
            return kakaoClient.kakaoAddressNameByCoordinate(
                            String.valueOf(parsed.get(0)),
                            String.valueOf(parsed.get(1))
                    )
                    .filter(name -> !name.isBlank())
                    .orElse(fallback);
        } catch (RuntimeException e) {
            return fallback;
        }
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
                .routePreference(defaultString(override.getRoutePreference(), defaults.getRoutePreference()))
                .avoidSteps(defaultBoolean(override.getAvoidSteps(), defaults.getAvoidSteps()))
                .avoidFords(defaultBoolean(override.getAvoidFords(), defaults.getAvoidFords()))
                .avoidFerries(defaultBoolean(override.getAvoidFerries(), defaults.getAvoidFerries()))
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
                .routePreference(defaultString(option.getRoutePreference(), defaults.getRoutePreference()))
                .avoidSteps(defaultBoolean(option.getAvoidSteps(), defaults.getAvoidSteps()))
                .avoidFords(defaultBoolean(option.getAvoidFords(), defaults.getAvoidFords()))
                .avoidFerries(defaultBoolean(option.getAvoidFerries(), defaults.getAvoidFerries()))
                .skillLevel(defaultString(option.getSkillLevel(), defaults.getSkillLevel()))
                .build();
    }

    private RouteOptionDto conservativeOption(RouteOptionDto base) {
        return RouteOptionDto.builder()
                .cyclingProfile(base.getCyclingProfile())
                .fastRoute(false)
                .routePreference(base.getRoutePreference())
                .avoidSteps(base.getAvoidSteps())
                .avoidFords(base.getAvoidFords())
                .avoidFerries(base.getAvoidFerries())
                .skillLevel(lowerSkillLevel(base.getSkillLevel()))
                .build();
    }

    private RouteOptionDto fastOption(RouteOptionDto base) {
        return RouteOptionDto.builder()
                .cyclingProfile(base.getCyclingProfile())
                .fastRoute(true)
                .routePreference(base.getRoutePreference())
                .avoidSteps(base.getAvoidSteps())
                .avoidFords(base.getAvoidFords())
                .avoidFerries(base.getAvoidFerries())
                .skillLevel(base.getSkillLevel())
                .build();
    }

    private RouteOptionDto roadOption(RouteOptionDto base, RecommendationDirective directive) {
        return RouteOptionDto.builder()
                .cyclingProfile(defaultString(directive.cyclingProfile(), "cycling-road"))
                .fastRoute(true)
                .routePreference(base.getRoutePreference())
                .avoidSteps(base.getAvoidSteps())
                .avoidFords(base.getAvoidFords())
                .avoidFerries(base.getAvoidFerries())
                .skillLevel(base.getSkillLevel())
                .build();
    }

    private RouteOptionDto bikeFriendlyOption(RouteOptionDto base, RecommendationDirective directive) {
        return RouteOptionDto.builder()
                .cyclingProfile(defaultString(directive.cyclingProfile(), "cycling-regular"))
                .fastRoute(false)
                .routePreference(base.getRoutePreference())
                .avoidSteps(true)
                .avoidFords(true)
                .avoidFerries(base.getAvoidFerries())
                .skillLevel(lowerSkillLevel(base.getSkillLevel()))
                .build();
    }

    private RouteOptionDto preferenceOption(RouteOptionDto base, String preference) {
        return RouteOptionDto.builder()
                .cyclingProfile(base.getCyclingProfile())
                .fastRoute("FASTEST".equals(preference))
                .routePreference(preference)
                .avoidSteps(base.getAvoidSteps())
                .avoidFords(base.getAvoidFords())
                .avoidFerries(base.getAvoidFerries())
                .skillLevel(base.getSkillLevel())
                .build();
    }

    private String optionSignature(RouteOptionDto option) {
        return String.join("|",
                defaultString(option.getCyclingProfile(), ""),
                String.valueOf(Boolean.TRUE.equals(option.getFastRoute())),
                defaultString(option.getRoutePreference(), ""),
                String.valueOf(Boolean.TRUE.equals(option.getAvoidSteps())),
                String.valueOf(Boolean.TRUE.equals(option.getAvoidFords())),
                String.valueOf(Boolean.TRUE.equals(option.getAvoidFerries())),
                defaultString(option.getSkillLevel(), "")
        );
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
        if (directive.preferPaved()) {
            weights.put("surface", weights.get("surface") + 0.20);
        }
        if (directive.preferBikeRoad() || directive.avoidMainRoad()) {
            weights.put("waytype", weights.get("waytype") + 0.20);
            weights.put("comfort", weights.get("comfort") + 0.10);
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
        if (directive.preferPaved()) {
            double unpaved = unpavedAmount(route);
            if (unpaved > 5.0) {
                score -= Math.min(0.30, (unpaved / 100.0) * 0.50);
            }
        }
        if ("UNPAVED".equals(directive.roadPreference())) {
            score += Math.min(0.30, (unpavedAmount(route) / 100.0) * 0.50);
        }
        if ("ROAD".equals(directive.roadPreference())) {
            double roadAmount = summaryAmount(route, "waytype", value -> Set.of(1, 2, 3).contains(value));
            if (roadAmount < 40.0) {
                score -= Math.min(0.30, ((40.0 - roadAmount) / 40.0) * 0.30);
            }
        }
        if (directive.preferBikeRoad()) {
            double bikeRoad = summaryAmount(route, "waytype", value -> Set.of(4, 6, 7).contains(value));
            if (bikeRoad < 20.0) {
                score -= Math.min(0.25, ((20.0 - bikeRoad) / 20.0) * 0.25);
            }
        }
        if (directive.avoidMainRoad()) {
            double mainRoad = summaryAmount(route, "waytype", value -> value == 1);
            if (mainRoad > 5.0) {
                score -= Math.min(0.30, (mainRoad / 100.0) * 0.60);
            }
        }
        return round4(Math.max(0.0, score));
    }

    private double unpavedAmount(ORSJsonResponse.Route route) {
        return Math.max(
                summaryAmount(route, "waytype", value -> value == 5),
                summaryAmount(route, "surface", value -> Set.of(2, 10, 11, 12, 15, 17).contains(value))
        );
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

    private String normalizedCyclingProfile(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value) {
            case "cycling-regular", "cycling-road", "cycling-mountain", "cycling-electric" -> value;
            default -> null;
        };
    }

    private String normalizedRoutePreference(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "FAST", "FASTEST" -> "FASTEST";
            case "SHORT", "SHORTEST" -> "SHORTEST";
            case "RECOMMENDED", "NORMAL" -> "RECOMMENDED";
            default -> null;
        };
    }

    private String normalizedRouteShape(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LONG", "LONGEST" -> "LONGEST";
            case "DETOUR", "SCENIC" -> "DETOUR";
            case "DIRECT" -> "DIRECT";
            default -> null;
        };
    }

    private String normalizedRoadPreference(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PAVED" -> "PAVED";
            case "BIKEWAY", "BIKE_ROAD", "CYCLEWAY" -> "BIKEWAY";
            case "UNPAVED", "DIRT", "GRAVEL" -> "UNPAVED";
            case "ROAD", "ROADWAY" -> "ROAD";
            case "QUIET" -> "QUIET";
            case "MAIN_ROAD_AVOID", "AVOID_MAIN_ROAD" -> "MAIN_ROAD_AVOID";
            default -> null;
        };
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

    private record ResolvedWaypoint(String coordinate, String name) {
    }

    private record WaypointEntry(
            String coordinate,
            String name,
            String typeCode,
            String contentId,
            String contentTypeId,
            int pathIndex
    ) {
    }

    private record RouteRebuildPlan(
            RouteRequestDto requestDto,
            List<RoutePathRespDto> routePrefix,
            List<RouteLocationNameRespDto> fullLocations
    ) {
    }

    private record SearchPoint(double lon, double lat) {
    }

    private record RecommendationDirective(
            Double maxDistanceKm,
            Integer targetDifficulty,
            boolean avoidConstruction,
            boolean avoidSteps,
            boolean avoidFords,
            boolean avoidFerries,
            boolean avoidIce,
            Boolean fastRoute,
            String routePreference,
            String routeShape,
            String cyclingProfile,
            boolean preferPaved,
            boolean preferBikeRoad,
            boolean avoidMainRoad,
            String roadPreference,
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
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    null,
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
