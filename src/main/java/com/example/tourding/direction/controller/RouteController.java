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
            description = "경로 찾기 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RouteSummaryRespDto.class),
                examples = @ExampleObject(
                    name = "성공 응답 예시",
                    value = """
                    {
                        "departureTime": "2025-08-01T17:58:32",
                        "distance": 17583,
                        "duration": 1479055,
                        "fuelPrice": 2095,
                        "taxiFare": 22200,
                        "tollFare": 0,
                        "startLon": 129.3881114,
                        "startLat": 36.1036573,
                        "goalLon": 129.3881114,
                        "goalLat": 36.1036573,
                        "goalDir": 1,
                        "bboxSwLon": 129.3185491,
                        "bboxSwLat": 36.0108079,
                        "bboxNeLon": 129.398555,
                        "bboxNeLat": 36.1074012,
                        "routeSections": [
                            {
                                "sequenceNum": 0,
                                "name": "희망대로659번길",
                                "congestion": 2,
                                "distance": 475,
                                "speed": 10,
                                "pointCount": 22,
                                "pointIndex": 1
                            }
                        ],
                        "routeGuides": [
                            {
                                "sequenceNum": 0,
                                "distance": 17,
                                "duration": 6119,
                                "instructions": "'희망대로659번길' 방면으로 우회전",
                                "pointIndex": 1,
                                "type": 3
                            }
                        ],
                        "routePaths": [
                            {
                                "sequenceNum": 0,
                                "lon": 129.3480647,
                                "lat": 36.0144849
                            }
                        ]
                    }
                    """
                )
            )
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
            description = "경로 안내 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(
                    type = "array",
                    implementation = RouteGuideRespDto.class
                ),
                examples = @ExampleObject(
                    name = "성공 응답 예시",
                    value = """
                    [
                        {
                            "sequenceNum": 0,
                            "distance": 17,
                            "duration": 6119,
                            "instructions": "'희망대로659번길' 방면으로 우회전",
                            "pointIndex": 1,
                            "type": 3
                        },
                        {
                            "sequenceNum": 1,
                            "distance": 475,
                            "duration": 157222,
                            "instructions": "'희망대로' 방면으로 우회전",
                            "pointIndex": 22,
                            "type": 3
                        }
                    ]
                    """
                )
            )
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
            description = "경로 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(
                    type = "array",
                    implementation = RoutePathRespDto.class
                ),
                examples = @ExampleObject(
                    name = "성공 응답 예시",
                    value = """
                    [
                        {
                            "sequenceNum": 0,
                            "lon": 129.3982599,
                            "lat": 36.0799804
                        },
                        {
                            "sequenceNum": 1,
                            "lon": 129.39825,
                            "lat": 36.0806747
                        }
                    ]
                    """
                )
            )
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
            description = "경로 구간 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(
                    type = "array",
                    implementation = RouteSectionRespDto.class
                ),
                examples = @ExampleObject(
                    name = "성공 응답 예시",
                    value = """
                    [
                        {
                            "sequenceNum": 0,
                            "name": "천마로",
                            "congestion": 1,
                            "distance": 1540,
                            "speed": 16,
                            "pointCount": 36,
                            "pointIndex": 4
                        },
                        {
                            "sequenceNum": 1,
                            "name": "영일만산단로",
                            "congestion": 1,
                            "distance": 1014,
                            "speed": 28,
                            "pointCount": 19,
                            "pointIndex": 39
                        }
                    ]
                    """
                )
            )
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
}
