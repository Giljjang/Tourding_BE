package com.example.tourding.direction.controller;

import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
@Tag(name = "Route API", description = "길찾기 관련 API")
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    @Operation(
        summary = "출발지, 도착지 입력해서 길찾기 API 호출, 경로 요약 반환",
        description = "사용자가 입력한 출발지, 도착지, 경유지를 바탕으로 최적 경로를 찾고 경로 요약 정보를 반환합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "경로 찾기 성공"
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public RouteSummaryRespDto getDirection(
        @Parameter(
            description = "경로 요청 정보",
            required = true,
            schema = @Schema(implementation = RouteRequestDto.class)
        )
        @RequestBody RouteRequestDto requestDto
    ) {
        return routeService.getRoute(requestDto);
    }

    @GetMapping
    @Operation(
            summary = "사용자 ID로 전체 경로 조회",
            description = "사용자의 가장 최근 길찾기 안내 경로를 조회합니다."
    )
    public RouteSummaryRespDto getRoute(
            @Parameter(
                    description = "사용자 id",
                    required = true
            )
            @RequestParam Long userId
    ) {
        return routeService.getRouteSummaryByUserId(userId);
    }

    @GetMapping("/guide")
    @Operation(
        summary = "사용자 ID로 경로 안내 조회",
        description = "특정 사용자의 경로 안내 정보를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "경로 안내 조회 성공"
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "사용자 또는 경로 정보를 찾을 수 없음")
    })
    public List<RouteGuideRespDto> getGuide(
        @Parameter(description = "사용자 ID", required = true, example = "1")
        @RequestParam Long userId
    ) {
        return routeService.getGuideByUserId(userId);
    }

    @GetMapping("/path")
    @Operation(
        summary = "사용자 ID로 경로 조회",
        description = "특정 사용자의 경로 좌표 정보를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "경로 조회 성공"
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "사용자 또는 경로 정보를 찾을 수 없음")
    })
    public List<RoutePathRespDto> getPath(
        @Parameter(description = "사용자 ID", required = true, example = "1")
        @RequestParam Long userId
    ) {
        return routeService.getPathByUserId(userId);
    }

    @GetMapping("/section")
    @Operation(
        summary = "사용자 ID로 경로 구간 조회",
        description = "특정 사용자의 경로 구간 정보를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "경로 구간 조회 성공"
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "사용자 또는 경로 정보를 찾을 수 없음")
    })
    public List<RouteSectionRespDto> getSection(
        @Parameter(description = "사용자 ID", required = true, example = "1")
        @RequestParam Long userId
    ) {
        return routeService.getSectionByUserId(userId);
    }

    @GetMapping("/location-name")
    @Operation(
            summary = "사용자 ID로 출발지,경유지,도착지 정보(이름, 위도, 경도, type) 조회",
            description = "사용자 ID로 출발지,경유지,도착지 정보를 받습니다." +
                    "sequenceNum : 출발지 ~ 도착지 순서" +
                    "name : 장소 이름" +
                    "type : 출발지/경유지/도착지를 나타내는 type 출발지 : Start, 경유지 : WayPoint, 도착지 : Goal" +
                    "lon : 경도" +
                    "lat : 위도"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공"
            ),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "사용자 또는 경로 정보를 찾을 수 없음")
    })
    public List<RouteLocationNameRespDto> getLocationName(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @RequestParam Long userId
    ) {
        return routeService.getLocationNameByUserId(userId);
    }
}
