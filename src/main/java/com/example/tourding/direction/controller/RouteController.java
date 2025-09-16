package com.example.tourding.direction.controller;

import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.service.RouteService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    @Operation(summary = "출발지, 도착지 입력해서 길찾기 API 호출, 경로 요약 반환")
    public RouteSummaryRespDto getDirection(@RequestBody RouteRequestDto requestDto) {
        RouteSummaryRespDto resp = routeService.getRoute(requestDto);
        log.info("✅ [SUCCESS] getDirection 호출 완료 - userId={}, start={}, goal={}",
                requestDto.getUserId(), requestDto.getStart(), requestDto.getGoal());
        return resp;
    }

    @GetMapping
    @Operation(summary = "사용자 ID로 전체 경로 조회")
    public RouteSummaryRespDto getRoute(@RequestParam Long userId) {
        RouteSummaryRespDto resp = routeService.getRouteSummaryByUserId(userId);
        log.info("✅ [SUCCESS] getRoute 호출 완료 - userId={}", userId);
        return resp;
    }

    @GetMapping("/guide")
    @Operation(summary = "사용자 ID로 경로 안내 조회")
    public List<RouteGuideRespDto> getGuide(@RequestParam Long userId) {
        List<RouteGuideRespDto> resp = routeService.getGuideByUserId(userId);
        log.info("✅ [SUCCESS] getGuide 호출 완료 - userId={}, 반환 개수={}", userId, resp.size());
        return resp;
    }

    @GetMapping("/path")
    @Operation(summary = "사용자 ID로 경로 조회")
    public List<RoutePathRespDto> getPath(@RequestParam Long userId) {
        List<RoutePathRespDto> resp = routeService.getPathByUserId(userId);
        log.info("✅ [SUCCESS] getPath 호출 완료 - userId={}, 반환 개수={}", userId, resp.size());
        return resp;
    }

    @GetMapping("/location-name")
    @Operation(summary = "사용자 ID로 출발지,경유지,도착지 정보 조회")
    public List<RouteLocationNameRespDto> getLocationName(@RequestParam Long userId) {
        List<RouteLocationNameRespDto> resp = routeService.getLocationNameByUserId(userId);
        log.info("✅ [SUCCESS] getLocationName 호출 완료 - userId={}, 반환 개수={}", userId, resp.size());
        return resp;
    }
}
