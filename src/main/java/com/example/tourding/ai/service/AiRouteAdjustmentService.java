package com.example.tourding.ai.service;

import com.example.tourding.ai.dto.*;
import com.example.tourding.ai.entity.AiRouteRequest;
import com.example.tourding.ai.entity.UserRidingProfile;
import com.example.tourding.ai.repository.AiRouteRequestRepository;
import com.example.tourding.ai.repository.UserRidingProfileRepository;
import com.example.tourding.direction.dto.RouteGuideRespDto;
import com.example.tourding.direction.dto.RouteOptionDto;
import com.example.tourding.direction.entity.RouteSummary;
import com.example.tourding.direction.repository.RouteSummaryRepository;
import com.example.tourding.direction.service.RouteService;
import com.example.tourding.enums.ErrorCode;
import com.example.tourding.exception.CustomException;
import com.example.tourding.user.entity.User;
import com.example.tourding.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AiRouteAdjustmentService {
    private final SpeechToTextService speechToTextService;
    private final IntentClassifierService intentClassifierService;
    private final RouteService routeService;
    private final UserRepository userRepository;
    private final RouteSummaryRepository routeSummaryRepository;
    private final AiRouteRequestRepository aiRouteRequestRepository;
    private final UserRidingProfileRepository userRidingProfileRepository;

    @Transactional
    public RouteGuideRespDto adjustByText(AiRouteAdjustReqDto requestDto) {
        return adjust(requestDto, requestDto.getMessage(), "TEXT", null);
    }

    @Transactional
    public RouteGuideRespDto adjustByVoice(Long userId, Long routeSummaryId, Double currentLon, Double currentLat, MultipartFile audioFile) {
        long sttStart = System.currentTimeMillis();
        String transcript;
        try {
            transcript = speechToTextService.transcribe(audioFile);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.AI_STT_FAILED);
        }
        int sttLatencyMs = (int) (System.currentTimeMillis() - sttStart);

        AiRouteAdjustReqDto requestDto = AiRouteAdjustReqDto.builder()
                .userId(userId)
                .routeSummaryId(routeSummaryId)
                .currentLon(currentLon)
                .currentLat(currentLat)
                .message(transcript)
                .build();

        return adjust(requestDto, transcript, "VOICE", sttLatencyMs);
    }

    @Transactional
    public UserRidingProfile saveRidingProfile(UserRidingProfileReqDto requestDto) {
        User user = userRepository.findById(requestDto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));

        UserRidingProfile profile = userRidingProfileRepository.findByUserId(user.getId())
                .orElse(UserRidingProfile.builder().user(user).build());

        RouteOptionDto routeOption = requestDto.getRouteOption();
        RouteOptionDto defaults = RouteOptionDto.defaults();
        profile.setCyclingProfile(defaultString(routeOption == null ? null : routeOption.getCyclingProfile(), defaults.getCyclingProfile()));
        profile.setSkillLevel(defaultString(routeOption == null ? null : routeOption.getSkillLevel(), defaults.getSkillLevel()));
        profile.setFastRoute(defaultBoolean(routeOption == null ? null : routeOption.getFastRoute(), defaults.getFastRoute()));
        profile.setAvoidSteps(defaultBoolean(routeOption == null ? null : routeOption.getAvoidSteps(), defaults.getAvoidSteps()));
        profile.setAvoidFords(defaultBoolean(routeOption == null ? null : routeOption.getAvoidFords(), defaults.getAvoidFords()));
        profile.setAvoidHills(false);
        profile.setPreferPaved(true);
        profile.setPreferBikeRoad(true);
        profile.setAvoidMainRoad(false);

        return userRidingProfileRepository.save(profile);
    }

    private RouteGuideRespDto adjust(AiRouteAdjustReqDto requestDto, String transcript, String inputType, Integer sttLatencyMs) {
        User user = userRepository.findById(requestDto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));
        RouteSummary routeSummary = routeSummaryRepository.findById(requestDto.getRouteSummaryId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTE_SUMMARY_NOT_FOUND));
        if (!routeSummary.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("사용자의 경로가 아닙니다.");
        }

        long llmStart = System.currentTimeMillis();
        AiIntentClassifyRespDto intent = intentClassifierService.classify(transcript);
        int llmLatencyMs = (int) (System.currentTimeMillis() - llmStart);

        AiRouteRequest aiRequest = AiRouteRequest.builder()
                .user(user)
                .routeSummary(routeSummary)
                .inputType(inputType)
                .transcript(transcript)
                .intent(intent.getIntent())
                .action(intent.getRouteAction())
                .status("PROCESSING")
                .currentLon(requestDto.getCurrentLon())
                .currentLat(requestDto.getCurrentLat())
                .sttLatencyMs(sttLatencyMs)
                .llmLatencyMs(llmLatencyMs)
                .build();
        aiRouteRequestRepository.save(aiRequest);

        if ("UNSUPPORTED".equals(intent.getIntent()) || !"RECALCULATE_REMAINING_ROUTE".equals(intent.getRouteAction())) {
            aiRequest.setStatus("REJECTED");
            aiRequest.setRejectionReason(intent.getExplanation());
            throw new CustomException(ErrorCode.AI_UNSUPPORTED_REQUEST);
        }

        long orsStart = System.currentTimeMillis();
        try {
            RouteGuideRespDto response = routeService.rebuildRouteFromCurrentLocation(
                    user.getId(),
                    routeSummary,
                    requestDto.getCurrentLon(),
                    requestDto.getCurrentLat(),
                    applyIntentOverride(requestDto.getRouteOption(), intent.getIntent())
            );
            aiRequest.setStatus("SUCCESS");
            aiRequest.setOrsLatencyMs((int) (System.currentTimeMillis() - orsStart));
            return response;
        } catch (CustomException e) {
            aiRequest.setStatus("FAILED");
            aiRequest.setRejectionReason(e.getMessage());
            throw e;
        } catch (Exception e) {
            aiRequest.setStatus("FAILED");
            aiRequest.setRejectionReason(e.getMessage());
            throw new CustomException(ErrorCode.AI_ROUTE_CANDIDATE_EMPTY);
        }
    }

    private RouteOptionDto applyIntentOverride(RouteOptionDto requestOption, String intent) {
        RouteOptionDto.RouteOptionDtoBuilder builder = requestOption == null
                ? RouteOptionDto.builder()
                : RouteOptionDto.builder()
                .cyclingProfile(requestOption.getCyclingProfile())
                .fastRoute(requestOption.getFastRoute())
                .avoidSteps(requestOption.getAvoidSteps())
                .avoidFords(requestOption.getAvoidFords())
                .skillLevel(requestOption.getSkillLevel());

        if ("LESS_HILLS".equals(intent)) {
            builder.skillLevel("BEGINNER");
        }
        if ("FASTER_ROUTE".equals(intent) || "SHORTER_ROUTE".equals(intent)) {
            builder.fastRoute(true);
        }
        if ("BETTER_SURFACE".equals(intent)) {
            builder.avoidSteps(true).avoidFords(true);
        }
        return builder.build();
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Boolean defaultBoolean(Boolean value, Boolean defaultValue) {
        return value == null ? defaultValue : value;
    }
}
