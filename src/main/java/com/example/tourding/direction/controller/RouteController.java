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
    @Operation(summary = "출발지, 도착지 입력해서 총시간, 총거리 조회")
    public RouteSummaryRespDto getDirection(@RequestBody RouteRequestDto requestDto) {
        RouteSummaryRespDto resp = routeService.getRoute(requestDto);
        log.info("✅ [SUCCESS] getDirection 호출 완료 - userId={}, start={}, goal={}",
                requestDto.getUserId(), requestDto.getStart(), requestDto.getGoal());
        return resp;
    }

    @GetMapping
    @Operation(summary = "사용자 ID로 총시간, 총거리, 검색인지, 실제 경로 탐색인지 조회")
    public RouteSummaryRespDto getRoute(@RequestParam Long userId, @RequestParam Boolean isUsed) {
        RouteSummaryRespDto resp = routeService.getRouteSummaryByUserId(userId, isUsed);
        log.info("✅ [SUCCESS] getRoute 호출 완료 - userId={}", userId);
        return resp;
    }

    @GetMapping("/guide")
    @Operation(summary = "사용자 ID로 경로 안내 조회")
    public List<RouteGuideRespDto> getGuide(@RequestParam Long userId, @RequestParam Boolean isUsed) {
        List<RouteGuideRespDto> resp = routeService.getGuideByUserId(userId, isUsed);
        log.info("✅ [SUCCESS] getGuide 호출 완료 - userId={}, 반환 개수={}", userId, resp.size());
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
    public RouteSummaryRespDto getRidingCourse(@RequestBody RouteByNameReqDto requestDto) {
        RouteSummaryRespDto resp = routeService.getRouteByName(requestDto);
        log.info("✅ [SUCCESS] getDirection 호출 완료 -, start={}, goal={}",
                requestDto.getStart(), requestDto.getGoal());
        return resp;
    }
}
