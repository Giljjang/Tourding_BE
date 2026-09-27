package com.example.tourding.ai.service;

import com.example.tourding.ai.dto.AiIntentClassifyRespDto;
import com.example.tourding.external.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
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
            List<String> queries = new java.util.ArrayList<>();
            if (text.contains("화장실")) {
                queries.add("화장실");
            }
            if (text.contains("편의점") || text.contains("보급") || text.contains("물살") || text.contains("물사")) {
                queries.add("편의점");
            }
            return result("FIND_FACILITY", "SEARCH_FACILITY", null,
                    "현재 위치에서 가장 가까운 편의시설을 경유하도록 분류했습니다.",
                    queries, "FACILITY_CATEGORY");
        }
        if (containsAny(text, "카페", "식당", "맛집")) {
            String query = concretePlaceQuery(text);
            if (query == null) {
                return result("UNSUPPORTED", "REJECT_WITH_ALTERNATIVE", null,
                        "카페·식당은 상호명이나 구체적인 장소명이 있을 때만 경유할 수 있습니다.");
            }
            return result("ADD_TOUR_SPOT", "ADD_WAYPOINT_CANDIDATE", null,
                    "구체적인 장소를 현재 위치 이후 경유하도록 분류했습니다.",
                    List.of(query), "PLACE_KEYWORD");
        }
        if (containsAny(text, "관광지", "볼거리", "들렀다", "들렸다", "들를", "들릴", "들러", "들리고", "들르고", "경유지", "거쳐", "갔다가", "찍고", "가자", "가고싶")) {
            String query = concretePlaceQuery(text);
            if (query != null) {
                return result("ADD_TOUR_SPOT", "ADD_WAYPOINT_CANDIDATE", null,
                        "구체적인 장소를 현재 위치 이후 경유하도록 분류했습니다.",
                        List.of(query), "PLACE_KEYWORD");
            }
            if (containsAny(text, "관광지", "볼거리", "들렀다", "들렸다", "들를", "들릴", "들러", "들리고", "들르고", "경유지", "거쳐", "갔다가", "찍고")) {
                return result("UNSUPPORTED", "REJECT_WITH_ALTERNATIVE", null,
                        "경유지는 상호명이나 구체적인 장소명이 있을 때만 추가할 수 있습니다.");
            }
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
        if (containsAny(text, "빠른길", "빠르게", "빨리", "시간덜", "시간을줄", "도착")) {
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

    private String concretePlaceQuery(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String candidate = text
                .replaceAll("(가다가|가는길에|가는 길에|경로상|근처|주변|한번|한 번|있으면|들러줘|들렸다가|들렸다|들려줘|들릴래|들르고싶어|들르고 싶어|들리고싶어|들리고 싶어|거쳐서|거쳐|갔다가|가자|가줘|싶어|싶어요)", " ")
                .replaceAll("(제일|가장|좀|더|빠르게|빠른|편한|쉬운|어려운|힘든|오르막|경사|길로|코스로|경로를|경로|길을|설정해줘|안내해줘|가는데|하는데|가고|원해|중에|중)", " ")
                .replaceAll("(카페|식당|맛집)", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (candidate.isBlank() || containsAny(candidate.replaceAll("\\s+", ""), "카페", "식당", "맛집", "화장실", "편의점")) {
            return null;
        }
        return candidate;
    }

    private AiIntentClassifyRespDto result(String intent, String action, Map<String, Double> weights, String explanation) {
        return result(intent, action, weights, explanation, null, null);
    }

    private AiIntentClassifyRespDto result(
            String intent,
            String action,
            Map<String, Double> weights,
            String explanation,
            List<String> waypointQueries,
            String waypointMode
    ) {
        return AiIntentClassifyRespDto.builder()
                .intent(intent)
                .confidence(0.95)
                .routeAction(action)
                .weightUpdate(weights)
                .explanation(explanation)
                .waypointQueries(waypointQueries)
                .waypointMode(waypointMode)
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
