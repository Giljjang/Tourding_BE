package com.example.tourding.ai.service;

import com.example.tourding.ai.dto.*;
import com.example.tourding.ai.entity.*;
import com.example.tourding.ai.repository.*;
import com.example.tourding.direction.entity.RouteSummary;
import com.example.tourding.direction.repository.RouteSummaryRepository;
import com.example.tourding.external.open_routes_service.*;
import com.example.tourding.user.entity.User;
import com.example.tourding.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiRouteAdjustmentService {
    private final SpeechToTextService speechToTextService;
    private final IntentClassifierService intentClassifierService;
    private final RouteScoringService routeScoringService;
    private final ORSCilent orsCilent;
    private final UserRepository userRepository;
    private final RouteSummaryRepository routeSummaryRepository;
    private final AiRouteRequestRepository aiRouteRequestRepository;
    private final AiRouteCandidateRepository aiRouteCandidateRepository;
    private final UserRidingProfileRepository userRidingProfileRepository;
    private final ObjectMapper objectMapper;

    @Value("${AI_CANDIDATE_COUNT:${ai.candidate-count:3}}")
    private int candidateCount;

    @Transactional
    public AiRouteAdjustRespDto adjustByText(AiRouteAdjustReqDto requestDto) {
        return adjust(requestDto, requestDto.getMessage(), "TEXT", null);
    }

    @Transactional
    public AiRouteAdjustRespDto adjustByVoice(Long userId, Long routeSummaryId, Double currentLon, Double currentLat, MultipartFile audioFile) {
        long sttStart = System.currentTimeMillis();
        String transcript = speechToTextService.transcribe(audioFile);
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

        profile.setCyclingProfile(defaultString(requestDto.getCyclingProfile(), "cycling-regular"));
        profile.setSkillLevel(defaultString(requestDto.getSkillLevel(), "NORMAL"));
        profile.setAvoidHills(defaultBoolean(requestDto.getAvoidHills(), false));
        profile.setPreferPaved(defaultBoolean(requestDto.getPreferPaved(), true));
        profile.setPreferBikeRoad(defaultBoolean(requestDto.getPreferBikeRoad(), true));
        profile.setAvoidMainRoad(defaultBoolean(requestDto.getAvoidMainRoad(), false));

        return userRidingProfileRepository.save(profile);
    }

    private AiRouteAdjustRespDto adjust(AiRouteAdjustReqDto requestDto, String transcript, String inputType, Integer sttLatencyMs) {
        User user = userRepository.findById(requestDto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("사용자 없음"));
        RouteSummary routeSummary = routeSummaryRepository.findById(requestDto.getRouteSummaryId())
                .orElseThrow(() -> new EntityNotFoundException("저장된 경로 없음"));
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

        if ("UNSUPPORTED".equals(intent.getIntent())) {
            aiRequest.setStatus("REJECTED");
            aiRequest.setRejectionReason(intent.getExplanation());
            return AiRouteAdjustRespDto.builder()
                    .requestId(aiRequest.getId())
                    .transcript(transcript)
                    .intent(intent.getIntent())
                    .action(intent.getRouteAction())
                    .status(aiRequest.getStatus())
                    .rejectionReason(intent.getExplanation())
                    .weightUpdate(intent.getWeightUpdate())
                    .candidates(Collections.emptyList())
                    .build();
        }

        if (!"RECALCULATE_REMAINING_ROUTE".equals(intent.getRouteAction())) {
            aiRequest.setStatus("SUCCESS");
            return AiRouteAdjustRespDto.builder()
                    .requestId(aiRequest.getId())
                    .transcript(transcript)
                    .intent(intent.getIntent())
                    .action(intent.getRouteAction())
                    .status(aiRequest.getStatus())
                    .weightUpdate(intent.getWeightUpdate())
                    .candidates(Collections.emptyList())
                    .build();
        }

        long orsStart = System.currentTimeMillis();
        List<CandidateDraft> drafts = buildCandidateDrafts(routeSummary, requestDto.getCurrentLon(), requestDto.getCurrentLat());
        List<RankedCandidate> rankedCandidates = new ArrayList<>();
        for (CandidateDraft draft : drafts) {
            ORSRouteAnalysisRequest analysisRequest = ORSRouteAnalysisRequest.builder()
                    .profile(resolveCyclingProfile(user.getId()))
                    .preference("recommended")
                    .coordinates(draft.coordinates())
                    .steepnessDifficulty(resolveSteepnessDifficulty(user.getId(), intent.getIntent()))
                    .build();

            ORSJsonResponse response = orsCilent.getRouteAnalysis(analysisRequest);
            if (response == null || response.getRoutes() == null || response.getRoutes().isEmpty()) {
                continue;
            }

            ORSJsonResponse.Route route = response.getRoutes().get(0);
            RouteScoringService.ScoreResult score = routeScoringService.score(route, intent.getWeightUpdate());
            rankedCandidates.add(new RankedCandidate(draft.name(), draft.reason(), route, score));
        }
        int orsLatencyMs = (int) (System.currentTimeMillis() - orsStart);
        aiRequest.setOrsLatencyMs(orsLatencyMs);

        rankedCandidates.sort(Comparator.comparingDouble((RankedCandidate c) -> c.score().total()).reversed());
        if (rankedCandidates.size() > candidateCount) {
            rankedCandidates = rankedCandidates.subList(0, candidateCount);
        }

        List<RouteCandidateRespDto> responseCandidates = new ArrayList<>();
        Long selectedCandidateId = null;
        for (int i = 0; i < rankedCandidates.size(); i++) {
            RankedCandidate ranked = rankedCandidates.get(i);
            boolean selected = i == 0;
            AiRouteCandidate saved = saveCandidate(aiRequest, ranked, intent.getWeightUpdate(), i + 1, selected);
            if (selected) {
                selectedCandidateId = saved.getId();
            }
            responseCandidates.add(toResponse(saved, ranked.reason()));
        }

        aiRequest.setStatus(responseCandidates.isEmpty() ? "FAILED" : "SUCCESS");
        if (responseCandidates.isEmpty()) {
            aiRequest.setRejectionReason("후보 경로 생성에 실패했습니다. 기존 경로를 유지합니다.");
        }

        return AiRouteAdjustRespDto.builder()
                .requestId(aiRequest.getId())
                .transcript(transcript)
                .intent(intent.getIntent())
                .action(intent.getRouteAction())
                .status(aiRequest.getStatus())
                .rejectionReason(aiRequest.getRejectionReason())
                .weightUpdate(intent.getWeightUpdate())
                .selectedCandidateId(selectedCandidateId)
                .candidates(responseCandidates)
                .build();
    }

    private AiRouteCandidate saveCandidate(AiRouteRequest aiRequest, RankedCandidate ranked, Map<String, Double> weights, int rankNo, boolean selected) {
        ORSJsonResponse.Summary summary = ranked.route().getSummary();
        AiRouteCandidate candidate = AiRouteCandidate.builder()
                .aiRouteRequest(aiRequest)
                .rankNo(rankNo)
                .score(ranked.score().total())
                .distance(summary.getDistance())
                .duration(summary.getDuration())
                .ascent(summary.getAscent())
                .descent(summary.getDescent())
                .comfortScore(ranked.score().comfort())
                .flatnessScore(ranked.score().flatness())
                .surfaceScore(ranked.score().surface())
                .waytypeScore(ranked.score().waytype())
                .efficiencyScore(ranked.score().efficiency())
                .weightJson(toJson(weights))
                .extraSummaryJson(toJson(ranked.route().getExtras()))
                .geometryJson(ranked.route().getGeometry())
                .selected(selected)
                .build();
        return aiRouteCandidateRepository.save(candidate);
    }

    private RouteCandidateRespDto toResponse(AiRouteCandidate candidate, String reason) {
        return RouteCandidateRespDto.builder()
                .candidateId(candidate.getId())
                .rank(candidate.getRankNo())
                .score(candidate.getScore())
                .distance(candidate.getDistance())
                .duration(candidate.getDuration())
                .ascent(candidate.getAscent())
                .descent(candidate.getDescent())
                .scoreDetail(RouteScoreDetailDto.builder()
                        .comfort(candidate.getComfortScore())
                        .flatness(candidate.getFlatnessScore())
                        .surface(candidate.getSurfaceScore())
                        .waytype(candidate.getWaytypeScore())
                        .efficiency(candidate.getEfficiencyScore())
                        .build())
                .reason(reason)
                .build();
    }

    private List<CandidateDraft> buildCandidateDrafts(RouteSummary routeSummary, Double currentLon, Double currentLat) {
        List<Double> current = List.of(currentLon, currentLat);
        List<Double> goal = parseCoordinate(routeSummary.getGoal());
        List<CandidateDraft> drafts = new ArrayList<>();

        drafts.add(new CandidateDraft("직접 재탐색", List.of(current, goal), "현재 위치에서 목적지까지 직접 재탐색한 후보입니다."));

        List<List<Double>> withExistingWaypoints = new ArrayList<>();
        withExistingWaypoints.add(current);
        withExistingWaypoints.addAll(parseWaypoints(routeSummary.getWayPoints()));
        withExistingWaypoints.add(goal);
        if (withExistingWaypoints.size() > 2) {
            drafts.add(new CandidateDraft("기존 경유지 유지", withExistingWaypoints, "기존 관광 경유지를 유지한 후보입니다."));
        }

        List<Double> midpoint = List.of(
                (currentLon + goal.get(0)) / 2.0 + 0.008,
                (currentLat + goal.get(1)) / 2.0
        );
        drafts.add(new CandidateDraft("우회 후보", List.of(current, midpoint, goal), "대체 경로 비교를 위해 중간 지점을 둔 후보입니다."));

        return drafts;
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

    private String resolveCyclingProfile(Long userId) {
        return userRidingProfileRepository.findByUserId(userId)
                .map(UserRidingProfile::getCyclingProfile)
                .filter(profile -> profile != null && !profile.isBlank())
                .orElse("cycling-regular");
    }

    private int resolveSteepnessDifficulty(Long userId, String intent) {
        if ("LESS_HILLS".equals(intent)) {
            return 0;
        }

        return userRidingProfileRepository.findByUserId(userId)
                .map(UserRidingProfile::getSkillLevel)
                .map(level -> switch (level) {
                    case "BEGINNER" -> 0;
                    case "ADVANCED" -> 2;
                    case "PRO" -> 3;
                    default -> 1;
                })
                .orElse(1);
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Boolean defaultBoolean(Boolean value, Boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private record CandidateDraft(String name, List<List<Double>> coordinates, String reason) {
    }

    private record RankedCandidate(String name, String reason, ORSJsonResponse.Route route, RouteScoringService.ScoreResult score) {
    }
}
