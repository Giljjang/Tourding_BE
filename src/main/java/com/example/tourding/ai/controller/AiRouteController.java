package com.example.tourding.ai.controller;

import com.example.tourding.ai.dto.*;
import com.example.tourding.ai.service.AiRouteAdjustmentService;
import com.example.tourding.direction.dto.RouteGuideRespDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI Route API", description = "AI 기반 경로 조정 API")
@Slf4j
public class AiRouteController {
    private final AiRouteAdjustmentService aiRouteAdjustmentService;

    @PostMapping("/routes/adjustments/text")
    @Operation(summary = "텍스트 명령으로 남은 경로 재조정")
    public RouteGuideRespDto adjustRouteByText(@RequestBody AiRouteAdjustReqDto requestDto) {
        RouteGuideRespDto response = aiRouteAdjustmentService.adjustByText(requestDto);
        log.info("✅ [SUCCESS] AI text adjustment - userId={}, routeSummaryId={}",
                requestDto.getUserId(), requestDto.getRouteSummaryId());
        return response;
    }

    @PostMapping(value = "/routes/adjustments/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "음성 명령으로 남은 경로 재조정")
    public RouteGuideRespDto adjustRouteByVoice(
            @RequestParam Long userId,
            @RequestParam Long routeSummaryId,
            @RequestParam Double currentLon,
            @RequestParam Double currentLat,
            @Parameter(
                    description = "음성 파일",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
            @RequestPart("audio") MultipartFile audio
    ) {
        RouteGuideRespDto response = aiRouteAdjustmentService.adjustByVoice(
                userId,
                routeSummaryId,
                currentLon,
                currentLat,
                audio
        );
        log.info("✅ [SUCCESS] AI voice adjustment - userId={}, routeSummaryId={}",
                userId, routeSummaryId);
        return response;
    }
}
