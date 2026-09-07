package com.example.tourding.ai.service;

import com.example.tourding.ai.dto.AiIntentClassifyRespDto;
import com.example.tourding.external.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntentClassifierService {
    private final OpenAiClient openAiClient;

    @Value("${AI_INTENT_RULE_FIRST:${ai.intent.rule-first:true}}")
    private boolean ruleFirst;

    public AiIntentClassifyRespDto classify(String transcript) {
        if (ruleFirst) {
            AiIntentClassifyRespDto ruleResult = classifyByRule(transcript);
            if (ruleResult != null) {
                return ruleResult;
            }
        }
        try {
            return openAiClient.classifyIntent(transcript);
        } catch (RuntimeException ignored) {
            AiIntentClassifyRespDto ruleResult = classifyByRule(transcript);
            if (ruleResult != null) {
                return ruleResult;
            }
            return result("UNSUPPORTED", "REJECT_WITH_ALTERNATIVE", null,
                    "지원하는 경로수정 조건으로 분류할 수 없습니다.");
        }
    }

    private AiIntentClassifyRespDto classifyByRule(String transcript) {
        String text = transcript == null ? "" : transcript.replaceAll("\\s+", "");

        if (containsAny(text, "절대", "100%", "무조건") && containsAny(text, "사고", "안전")) {
            return result("UNSUPPORTED", "REJECT_WITH_ALTERNATIVE", null,
                    "현재 데이터로 사고가 절대 나지 않는 경로는 보장할 수 없습니다.");
        }
        if (containsAny(text, "운세", "풍수", "행운")) {
            return result("UNSUPPORTED", "REJECT_WITH_ALTERNATIVE", null,
                    "운세나 방향 운 같은 요청은 경로 데이터로 판단할 수 없습니다.");
        }
        if (containsAny(text, "화장실", "편의점", "물살", "물사", "보급")) {
            return result("FIND_FACILITY", "SEARCH_FACILITY", null,
                    "편의시설 탐색 요청으로 분류했습니다.");
        }
        if (containsAny(text, "관광지", "볼거리", "카페", "들렀다", "들렸다", "경유지")) {
            return result("ADD_TOUR_SPOT", "ADD_WAYPOINT_CANDIDATE", null,
                    "관광지 또는 경유지 추가 요청으로 분류했습니다.");
        }
        if (containsAny(text, "오르막", "업힐", "경사", "평지", "완만", "평탄", "언덕", "덜힘든", "덜힘들")) {
            return result("LESS_HILLS", "RECALCULATE_REMAINING_ROUTE",
                    weights(0.20, 0.45, 0.15, 0.10, 0.10),
                    "오르막 회피 요청으로 분류해 평탄함 가중치를 높였습니다.");
        }
        if (containsAny(text, "비포장", "자갈", "흙길", "아스팔트", "포장", "노면")) {
            return result("BETTER_SURFACE", "RECALCULATE_REMAINING_ROUTE",
                    weights(0.15, 0.15, 0.45, 0.15, 0.10),
                    "노면 상태 선호 요청으로 분류해 노면 가중치를 높였습니다.");
        }
        if (containsAny(text, "자전거도로", "자전거길", "편한길", "편한", "편하게", "쉬운", "쉽게", "무난", "안전한코스", "차랑덜")) {
            return result("BIKE_FRIENDLY", "RECALCULATE_REMAINING_ROUTE",
                    weights(0.35, 0.15, 0.15, 0.25, 0.10),
                    "자전거 친화 경로 요청으로 분류했습니다.");
        }
        if (containsAny(text, "빠른길", "빨리", "시간덜", "도착")) {
            return result("FASTER_ROUTE", "RECALCULATE_REMAINING_ROUTE",
                    weights(0.10, 0.10, 0.10, 0.10, 0.60),
                    "빠른 경로 요청으로 분류해 효율 가중치를 높였습니다.");
        }
        if (containsAny(text, "짧은길", "가까운길", "우회", "돌아가지")) {
            return result("SHORTER_ROUTE", "RECALCULATE_REMAINING_ROUTE",
                    weights(0.10, 0.15, 0.10, 0.10, 0.55),
                    "짧은 경로 요청으로 분류해 효율 가중치를 높였습니다.");
        }
        if (containsAny(text, "큰도로", "차많은", "간선도로", "차도", "조용한길")) {
            return result("AVOID_ROAD", "RECALCULATE_REMAINING_ROUTE",
                    weights(0.25, 0.15, 0.10, 0.40, 0.10),
                    "큰길 회피 요청으로 분류해 길 유형 가중치를 높였습니다.");
        }

        return null;
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private AiIntentClassifyRespDto result(String intent, String action, Map<String, Double> weights, String explanation) {
        return AiIntentClassifyRespDto.builder()
                .intent(intent)
                .confidence(0.95)
                .routeAction(action)
                .weightUpdate(weights)
                .explanation(explanation)
                .build();
    }

    public Map<String, Double> weights(double comfort, double flatness, double surface, double waytype, double efficiency) {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("comfort", comfort);
        result.put("flatness", flatness);
        result.put("surface", surface);
        result.put("waytype", waytype);
        result.put("efficiency", efficiency);
        return result;
    }
}
