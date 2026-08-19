package com.example.tourding.direction.controller;

import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.service.RouteService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
@Tag(name = "Route API", description = "길찾기 관련 API")
@Slf4j
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    @Operation(summary = "출발지, 도착지 입력해서 통합 코스 정보 조회")
    public RouteGuideRespDto getDirection(@RequestBody RouteRequestDto requestDto) {
        RouteGuideRespDto resp = routeService.getRoute(requestDto);
        log.info("✅ [SUCCESS] getDirection 호출 완료 - userId={}, start={}, goal={}",
                requestDto.getUserId(), requestDto.getStart(), requestDto.getGoal());
        return resp;
    }

    @GetMapping
    @Operation(summary = "사용자 ID로 통합 코스 정보 조회")
    public RouteGuideRespDto getRoute(@RequestParam Long userId, @RequestParam Boolean isUsed) {
        RouteGuideRespDto resp = routeService.getRouteSummaryByUserId(userId, isUsed);
        log.info("✅ [SUCCESS] getRoute 호출 완료 - userId={}", userId);
        return resp;
    }

    @GetMapping("/guide")
    @Operation(summary = "사용자 ID로 경로 안내 조회")
    public RouteGuideRespDto getGuide(@RequestParam Long userId, @RequestParam Boolean isUsed) {
        RouteGuideRespDto resp = routeService.getGuideByUserId(userId, isUsed);
        log.info("✅ [SUCCESS] getGuide 호출 완료 - userId={}", userId);
        return resp;
    }

    @PostMapping("/recommendations")
    @Operation(summary = "사용자 라이딩 정보 기반 추천 경로 3개 조회")
    public RouteRecommendationsRespDto getRouteRecommendations(@RequestBody RouteRecommendationReqDto requestDto) {
        RouteRecommendationsRespDto resp = routeService.getRouteRecommendations(requestDto);
        log.info("✅ [SUCCESS] getRouteRecommendations 호출 완료 - userId={}, 반환 개수={}",
                requestDto.getUserId(), resp.getRoutes() == null ? 0 : resp.getRoutes().size());
        return resp;
    }

    @GetMapping("/path")
    @Operation(summary = "사용자 ID로 경로 조회")
    public List<RoutePathRespDto> getPath(@RequestParam Long userId, @RequestParam Boolean isUsed) {
        List<RoutePathRespDto> resp = routeService.getPathByUserId(userId, isUsed);
        log.info("✅ [SUCCESS] getPath 호출 완료 - userId={}, 반환 개수={}", userId, resp.size());
        return resp;
    }

    @GetMapping("/location-name")
    @Operation(summary = "사용자 ID로 출발지,경유지,도착지 정보 조회")
    public List<RouteLocationNameRespDto> getLocationName(@RequestParam Long userId, @RequestParam Boolean isUsed) {
        List<RouteLocationNameRespDto> resp = routeService.getLocationNameByUserId(userId, isUsed);
        log.info("✅ [SUCCESS] getLocationName 호출 완료 - userId={}, 반환 개수={}", userId, resp.size());
        return resp;
    }

    @GetMapping("/riding-recommend")
    @Operation(summary = "추천 라이딩코스 받기")
    public List<RouteRidingRecomDto> getLocationName(@RequestParam int pageNum) {
        List<RouteRidingRecomDto> resp = routeService.getRidingRecommend(pageNum);
        log.info("✅ [SUCCESS] getLocationName 호출 완료 - 페이지 번호={}, 반환 개수={}", pageNum, resp.size());
        return resp;
    }

    @PostMapping("/by-name")
    @Operation(summary = "추천 라이딩코스 받기")
    public RouteGuideRespDto getRidingCourse(@RequestBody RouteByNameReqDto requestDto) {
        RouteGuideRespDto resp = routeService.getRouteByName(requestDto);
        log.info("✅ [SUCCESS] getDirection 호출 완료 -, start={}, goal={}",
                requestDto.getStart(), requestDto.getGoal());
        return resp;
    }

    @PostMapping("/{routeSummaryId}/rollback")
    @Operation(summary = "AI 경로 변경 전 이전 경로로 되돌리기")
    public RouteGuideRespDto rollbackRoute(
            @PathVariable Long routeSummaryId,
            @RequestBody RouteRollbackReqDto requestDto
    ) {
        RouteGuideRespDto resp = routeService.rollbackRoute(routeSummaryId, requestDto.getUserId());
        log.info("✅ [SUCCESS] rollbackRoute 호출 완료 - userId={}, routeSummaryId={}",
                requestDto.getUserId(), routeSummaryId);
        return resp;
    }
}
