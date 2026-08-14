package com.example.tourding.ai.controller;

import com.example.tourding.ai.dto.*;
import com.example.tourding.ai.entity.UserRidingProfile;
import com.example.tourding.ai.service.AiRouteAdjustmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI Route API", description = "AI 기반 경로 조정 API")
@Slf4j
public class AiRouteController {
    private final AiRouteAdjustmentService aiRouteAdjustmentService;

    @PostMapping("/routes/adjustments/text")
    @Operation(summary = "텍스트 명령으로 남은 경로 재조정")
    public AiRouteAdjustRespDto adjustRouteByText(@RequestBody AiRouteAdjustReqDto requestDto) {
        AiRouteAdjustRespDto response = aiRouteAdjustmentService.adjustByText(requestDto);
        log.info("✅ [SUCCESS] AI text adjustment - userId={}, routeSummaryId={}, intent={}, status={}",
                requestDto.getUserId(), requestDto.getRouteSummaryId(), response.getIntent(), response.getStatus());
        return response;
    }

    @PostMapping("/routes/adjustments/voice")
    @Operation(summary = "음성 명령으로 남은 경로 재조정")
    public AiRouteAdjustRespDto adjustRouteByVoice(
            @RequestParam Long userId,
            @RequestParam Long routeSummaryId,
            @RequestParam Double currentLon,
            @RequestParam Double currentLat,
            @RequestPart MultipartFile audio
    ) {
        AiRouteAdjustRespDto response = aiRouteAdjustmentService.adjustByVoice(
                userId,
                routeSummaryId,
                currentLon,
                currentLat,
                audio
        );
        log.info("✅ [SUCCESS] AI voice adjustment - userId={}, routeSummaryId={}, intent={}, status={}",
                userId, routeSummaryId, response.getIntent(), response.getStatus());
        return response;
    }

    @PostMapping("/riding-profile")
    @Operation(summary = "AI 경로 조정용 사용자 라이딩 프로필 저장")
    public Map<String, Object> saveRidingProfile(@RequestBody UserRidingProfileReqDto requestDto) {
        UserRidingProfile profile = aiRouteAdjustmentService.saveRidingProfile(requestDto);
        return Map.of(
                "profileId", profile.getId(),
                "userId", requestDto.getUserId(),
                "cyclingProfile", profile.getCyclingProfile(),
                "skillLevel", profile.getSkillLevel(),
                "avoidHills", profile.getAvoidHills(),
                "preferPaved", profile.getPreferPaved(),
                "preferBikeRoad", profile.getPreferBikeRoad(),
                "avoidMainRoad", profile.getAvoidMainRoad()
        );
    }
}
