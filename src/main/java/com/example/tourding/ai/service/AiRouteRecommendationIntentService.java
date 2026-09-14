package com.example.tourding.ai.service;

import com.example.tourding.ai.dto.AiRouteRecommendationIntentDto;
import com.example.tourding.external.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiRouteRecommendationIntentService {
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:km|KM|킬로|키로)");

    private final OpenAiClient openAiClient;

    @Value("${AI_INTENT_RULE_FIRST:${ai.intent.rule-first:true}}")
    private boolean ruleFirst = true;

    @Value("${AI_RECOMMENDATION_INTENT_AI_FIRST:${ai.recommendation.intent.ai-first:true}}")
    private boolean recommendationAiFirst = true;

    public AiRouteRecommendationIntentDto classify(String text) {
        if (text == null || text.isBlank()) {
            return AiRouteRecommendationIntentDto.builder()
                    .supported(false)
                    .build();
        }

        if (recommendationAiFirst) {
            AiRouteRecommendationIntentDto aiResult = classifyByAi(text);
            if (hasAnyCondition(aiResult) || (aiResult != null && aiResult.isSupported())) {
                return sanitizeResult(aiResult);
            }
            if (isExplicitUnsupported(aiResult)) {
                return sanitizeResult(aiResult);
            }
        }

        String compactText = text.replaceAll("\\s+", "");
        if (isFacilitySearch(compactText) && !hasWaypointDirective(compactText)) {
            return unsupported("추천 코스 조건이 아닌 시설 탐색 요청입니다.");
        }

        if (ruleFirst) {
            AiRouteRecommendationIntentDto ruleResult = classifyByRule(text);
            if (hasAnyCondition(ruleResult) || ruleResult.isSupported() || isExplicitUnsupported(ruleResult)) {
                return sanitizeResult(ruleResult);
            }
        }

        AiRouteRecommendationIntentDto aiResult = classifyByAi(text);
        if (hasAnyCondition(aiResult) || (aiResult != null && aiResult.isSupported())) {
            return sanitizeResult(aiResult);
        }

        return sanitizeResult(classifyByRule(text));
    }

    private AiRouteRecommendationIntentDto classifyByAi(String text) {
        try {
            return openAiClient.classifyRouteRecommendationIntent(text);
        } catch (RuntimeException ignored) {
            // OpenAI 장애/키 누락 시에도 추천 기능 자체는 허용된 조건 안에서 계속 동작한다.
            return null;
        }
    }

    private AiRouteRecommendationIntentDto sanitizeResult(AiRouteRecommendationIntentDto result) {
        if (result == null) {
            return null;
        }
        List<String> waypointNames = result.getWaypointNames() == null
                ? List.of()
                : result.getWaypointNames().stream()
                .flatMap(name -> splitWaypointNames(name).stream())
                .filter(name -> name.length() >= 2)
                .filter(name -> !isGenericFacilityName(name))
                .distinct()
                .toList();
        result.setWaypointNames(waypointNames);
        return result;
    }

    private AiRouteRecommendationIntentDto classifyByRule(String rawText) {
        String text = rawText.replaceAll("\\s+", "");
        if (isFacilitySearch(text) && !hasWaypointDirective(text)) {
            return unsupported("추천 코스 조건이 아닌 시설 탐색 요청입니다.");
        }

        List<String> waypointNames = waypointNames(rawText);
        Integer targetDifficulty = targetDifficulty(text);
        Boolean avoidConstruction = containsAny(text, "공사", "통제", "폐쇄") ? true : null;
        Boolean avoidSteps = containsAny(text, "계단") ? true : null;
        Boolean avoidFords = containsAny(text, "물길", "하천", "개울", "침수", "도섭") ? true : null;
        Boolean avoidIce = containsAny(text, "빙판", "눈길", "얼음", "결빙") ? true : null;
        Boolean fastRoute = fastRoute(text);
        String cyclingProfile = cyclingProfile(text);
        Boolean preferPaved = preferPaved(text);
        Boolean preferBikeRoad = preferBikeRoad(text);
        Boolean avoidMainRoad = avoidMainRoad(text);
        Double maxDistanceKm = maxDistanceKm(rawText);

        boolean supported = !waypointNames.isEmpty()
                || targetDifficulty != null
                || avoidConstruction != null
                || avoidSteps != null
                || avoidFords != null
                || avoidIce != null
                || fastRoute != null
                || cyclingProfile != null
                || preferPaved != null
                || preferBikeRoad != null
                || avoidMainRoad != null
                || maxDistanceKm != null;

        return AiRouteRecommendationIntentDto.builder()
                .waypointNames(waypointNames)
                .targetDifficulty(targetDifficulty)
                .avoidConstruction(avoidConstruction)
                .avoidSteps(avoidSteps)
                .avoidFords(avoidFords)
                .avoidIce(avoidIce)
                .fastRoute(fastRoute)
                .cyclingProfile(cyclingProfile)
                .preferPaved(preferPaved)
                .preferBikeRoad(preferBikeRoad)
                .avoidMainRoad(avoidMainRoad)
                .maxDistanceKm(maxDistanceKm)
                .weightUpdate(weightsFor(targetDifficulty, avoidConstruction, avoidSteps, avoidFords, avoidIce,
                        fastRoute, cyclingProfile, preferPaved, preferBikeRoad, avoidMainRoad))
                .explanation(supported ? "추천 코스 조건으로 분류했습니다." : unsupportedExplanation(text, waypointNames))
                .supported(supported)
                .build();
    }

    private AiRouteRecommendationIntentDto unsupported(String explanation) {
        return AiRouteRecommendationIntentDto.builder()
                .waypointNames(List.of())
                .explanation(explanation)
                .supported(false)
                .build();
    }

    private List<String> waypointNames(String text) {
        List<String> result = new ArrayList<>();
        addWaypointMatches(result, text, "경유지\\s*[:：]?\\s*([^,，.。]+)");
        addWaypointMatches(result, text, "([^,，.。]{2,30}?)(?:을|를)?\\s*(?:경유|들렀다가|들렀다|들렸다가|들렸다|들러서|들러|들려서|들려|들리고\\s*싶어요|들리고\\s*싶어|들리고싶어요|들리고싶어|들리자|거쳐서|거쳐|거치고|찍고|갔다가|가자|돌아갔다가|돌아가서|돌아서)");
        return result.stream()
                .flatMap(name -> splitWaypointNames(name).stream())
                .filter(name -> name.length() >= 2)
                .filter(name -> !isGenericFacilityName(name))
                .distinct()
                .toList();
    }

    private void addWaypointMatches(List<String> result, String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
    }

    private List<String> splitWaypointNames(String value) {
        String cleaned = cleanWaypointName(value);
        if (cleaned.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cleaned
                        .replaceAll("\\s*(?:,|，|/|\\||\\+|&|그리고|및)\\s*", "|")
                        .replaceAll("(?:이랑|랑|하고|와|과)\\s+", "|")
                        .split("\\|"))
                .map(this::cleanWaypointName)
                .filter(name -> !name.isBlank())
                .toList();
    }

    private String cleanWaypointName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        String previous;
        do {
            previous = cleaned;
            cleaned = cleaned
                    .replaceAll("^.*(?:피하고|피해서|빼고|제외하고|설정해주고|추천해주고|여유롭게|빠른길로|위주로|코스로|길로|길류|길을|가는\\s*데|가는는데|가는데|가다가)\\s*", "")
                    .replaceAll("^.*(?:에서)\\s*", "")
                    .replaceAll("^(가다가|가는\\s*길에|중간에|도중에|오는\\s*길에|가는길에|오는길에|잠깐|한번|좀|근처에|근처|주변에|주변)\\s*", "")
                    .trim();
        } while (!previous.equals(cleaned));
        return cleaned
                .replaceAll("(가는길에|중간에|그리고|다음|먼저|코스에|추가|포함|해서|하고|갔다가|갔다|다가|줘|주세요|으로|로)$", "")
                .replaceAll("(도|을|를|에|에서)?\\s*(한번|잠깐|좀)$", "")
                .replaceAll("(도|을|를)$", "")
                .replaceAll("(난이도|쉬운|보통|어려운|상급|초보|키로|킬로|km|KM|이하|미만|정도)", "")
                .trim();
    }

    private Integer targetDifficulty(String text) {
        if (containsAny(text, "난이도4", "4단계", "상급", "전문가", "힘든", "빡센", "도전")) {
            return 4;
        }
        if (containsAny(text, "난이도3", "3단계", "어려운", "숙련", "오르막많", "오르막이많", "업힐많", "경사많", "언덕많")) {
            return 3;
        }
        if (containsAny(text, "난이도2", "2단계", "보통", "일반")) {
            return 2;
        }
        if (containsAny(text,
                "난이도1", "1단계", "쉬운", "쉽게", "초보", "편한", "낮은난이도",
                "오르막피", "오르막없", "오르막적", "업힐피", "업힐없", "업힐적",
                "경사피", "경사없", "경사적", "언덕피", "언덕없", "언덕적",
                "평지", "완만", "평탄", "덜힘든", "덜힘들", "힘들지않은", "힘들지않게")) {
            return 1;
        }
        return null;
    }

    private Double maxDistanceKm(String text) {
        Matcher matcher = DISTANCE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return Double.parseDouble(matcher.group(1));
    }

    private Boolean fastRoute(String text) {
        if (containsAny(text, "빠른", "빨리", "최단시간", "시간짧", "효율", "제일빠른", "가장빠른")) {
            return true;
        }
        if (containsAny(text, "천천히", "여유", "느긋")) {
            return false;
        }
        return null;
    }

    private String cyclingProfile(String text) {
        if (containsAny(text, "로드", "로드바이크", "싸이클", "사이클")) {
            return "cycling-road";
        }
        if (containsAny(text, "산악", "mtb", "MTB", "임도", "트레일", "산길")) {
            return "cycling-mountain";
        }
        if (containsAny(text, "전기자전거", "전기", "전동", "이바이크", "ebike", "e-bike")) {
            return "cycling-electric";
        }
        if (containsAny(text, "일반자전거", "일반")) {
            return "cycling-regular";
        }
        return null;
    }

    private Boolean preferPaved(String text) {
        if (containsAny(text, "포장도로", "포장길", "아스팔트", "노면좋", "노면좋은", "자갈피", "흙길피", "비포장피")) {
            return true;
        }
        return null;
    }

    private Boolean preferBikeRoad(String text) {
        if (containsAny(text, "자전거도로", "자전거길", "산책로", "강변길", "차없는", "차없는길")) {
            return true;
        }
        return null;
    }

    private Boolean avoidMainRoad(String text) {
        if (containsAny(text, "큰도로피", "큰길피", "간선도로피", "차도피", "차많", "차적은", "조용한길", "골목길")) {
            return true;
        }
        return null;
    }

    private Map<String, Double> weightsFor(
            Integer targetDifficulty,
            Boolean avoidConstruction,
            Boolean avoidSteps,
            Boolean avoidFords,
            Boolean avoidIce,
            Boolean fastRoute,
            String cyclingProfile,
            Boolean preferPaved,
            Boolean preferBikeRoad,
            Boolean avoidMainRoad
    ) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("comfort", 0.25);
        weights.put("flatness", 0.25);
        weights.put("surface", 0.20);
        weights.put("waytype", 0.20);
        weights.put("efficiency", 0.10);

        if (targetDifficulty != null && targetDifficulty <= 2) {
            weights.put("comfort", 0.30);
            weights.put("flatness", 0.35);
            weights.put("surface", 0.15);
            weights.put("waytype", 0.15);
            weights.put("efficiency", 0.05);
        }
        if (targetDifficulty != null && targetDifficulty >= 4) {
            weights.put("comfort", 0.15);
            weights.put("flatness", 0.15);
            weights.put("surface", 0.15);
            weights.put("waytype", 0.15);
            weights.put("efficiency", 0.40);
        }
        if (Boolean.TRUE.equals(fastRoute)) {
            weights.put("efficiency", Math.max(weights.get("efficiency"), 0.45));
            weights.put("flatness", Math.min(weights.get("flatness"), 0.20));
            weights.put("comfort", Math.min(weights.get("comfort"), 0.20));
        }
        if (Boolean.FALSE.equals(fastRoute)) {
            weights.put("comfort", weights.get("comfort") + 0.10);
            weights.put("flatness", weights.get("flatness") + 0.10);
            weights.put("efficiency", Math.max(0.05, weights.get("efficiency") - 0.10));
        }
        if ("cycling-road".equals(cyclingProfile) || Boolean.TRUE.equals(preferPaved)) {
            weights.put("surface", weights.get("surface") + 0.20);
            weights.put("efficiency", weights.get("efficiency") + 0.05);
        }
        if (Boolean.TRUE.equals(preferBikeRoad) || Boolean.TRUE.equals(avoidMainRoad)) {
            weights.put("waytype", weights.get("waytype") + 0.20);
            weights.put("comfort", weights.get("comfort") + 0.10);
        }
        if (Boolean.TRUE.equals(avoidConstruction)
                || Boolean.TRUE.equals(avoidSteps)
                || Boolean.TRUE.equals(avoidFords)
                || Boolean.TRUE.equals(avoidIce)) {
            weights.put("surface", weights.get("surface") + 0.10);
            weights.put("waytype", weights.get("waytype") + 0.10);
            weights.put("efficiency", Math.max(0.05, weights.get("efficiency") - 0.10));
        }
        return normalize(weights);
    }

    private Map<String, Double> normalize(Map<String, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return weights;
        }
        weights.replaceAll((key, value) -> Math.round((value / total) * 10_000.0) / 10_000.0);
        return weights;
    }

    private boolean hasAnyCondition(AiRouteRecommendationIntentDto result) {
        return result != null && (
                (result.getWaypointNames() != null && !result.getWaypointNames().isEmpty())
                        || result.getTargetDifficulty() != null
                        || result.getAvoidConstruction() != null
                        || result.getAvoidSteps() != null
                        || result.getAvoidFords() != null
                        || result.getAvoidIce() != null
                        || result.getFastRoute() != null
                        || result.getCyclingProfile() != null
                        || result.getPreferPaved() != null
                        || result.getPreferBikeRoad() != null
                        || result.getAvoidMainRoad() != null
                        || result.getMaxDistanceKm() != null
        );
    }

    private boolean isExplicitUnsupported(AiRouteRecommendationIntentDto result) {
        return result != null
                && !result.isSupported()
                && result.getExplanation() != null
                && !result.getExplanation().isBlank()
                && !"지원하지 않는 추천 조건입니다.".equals(result.getExplanation());
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFacilitySearch(String text) {
        return containsAny(text, "카페", "화장실", "편의점", "맛집", "식당", "보급");
    }

    private boolean hasWaypointDirective(String text) {
        return containsAny(text, "경유", "경유지", "들러", "들렀", "들렸", "들렸다", "들려",
                "들리고싶어", "들리고싶어요", "들리자", "거쳐", "거치", "찍고", "갔다가", "가자",
                "돌아갔다", "돌아가서", "돌아서");
    }

    private boolean isGenericFacilityName(String name) {
        String compact = name.replaceAll("\\s+", "");
        return Set.of("카페", "화장실", "편의점", "맛집", "식당", "보급").contains(compact);
    }

    private String unsupportedExplanation(String text, List<String> waypointNames) {
        if (isFacilitySearch(text) && hasWaypointDirective(text) && (waypointNames == null || waypointNames.isEmpty())) {
            return "경유지는 시설 종류가 아니라 구체적인 장소명으로 입력해야 합니다.";
        }
        return "지원하지 않는 추천 조건입니다.";
    }
}
