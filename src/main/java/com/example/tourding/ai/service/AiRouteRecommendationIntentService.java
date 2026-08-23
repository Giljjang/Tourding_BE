package com.example.tourding.ai.service;

import com.example.tourding.ai.dto.AiRouteRecommendationIntentDto;
import com.example.tourding.external.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiRouteRecommendationIntentService {
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:km|KM|킬로|키로)");

    private final OpenAiClient openAiClient;

    public AiRouteRecommendationIntentDto classify(String text) {
        if (text == null || text.isBlank()) {
            return AiRouteRecommendationIntentDto.builder()
                    .supported(false)
                    .build();
        }

        try {
            AiRouteRecommendationIntentDto aiResult = openAiClient.classifyRouteRecommendationIntent(text);
            if (hasAnyCondition(aiResult) || aiResult.isSupported()) {
                return aiResult;
            }
        } catch (RuntimeException ignored) {
            // OpenAI 장애/키 누락 시에도 추천 기능 자체는 허용된 조건 안에서 계속 동작한다.
        }

        return classifyByRule(text);
    }

    private AiRouteRecommendationIntentDto classifyByRule(String rawText) {
        String text = rawText.replaceAll("\\s+", "");
        List<String> waypointNames = waypointNames(rawText);
        Integer targetDifficulty = targetDifficulty(text);
        Boolean avoidConstruction = containsAny(text, "공사", "통제", "폐쇄") ? true : null;
        Boolean avoidSteps = containsAny(text, "계단") ? true : null;
        Boolean avoidIce = containsAny(text, "빙판", "눈길", "얼음", "결빙") ? true : null;
        Double maxDistanceKm = maxDistanceKm(rawText);

        boolean supported = !waypointNames.isEmpty()
                || targetDifficulty != null
                || avoidConstruction != null
                || avoidSteps != null
                || avoidIce != null
                || maxDistanceKm != null;

        return AiRouteRecommendationIntentDto.builder()
                .waypointNames(waypointNames)
                .targetDifficulty(targetDifficulty)
                .avoidConstruction(avoidConstruction)
                .avoidSteps(avoidSteps)
                .avoidIce(avoidIce)
                .maxDistanceKm(maxDistanceKm)
                .weightUpdate(weightsFor(targetDifficulty, avoidConstruction, avoidSteps, avoidIce))
                .explanation(supported ? "추천 코스 조건으로 분류했습니다." : "지원하지 않는 추천 조건입니다.")
                .supported(supported)
                .build();
    }

    private List<String> waypointNames(String text) {
        List<String> result = new ArrayList<>();
        addWaypointMatches(result, text, "경유지\\s*[:：]?\\s*([^,，.。]+)");
        addWaypointMatches(result, text, "([^,，.。]{2,30}?)(?:을|를)?\\s*(?:경유|들러|들렀|들렸|거쳐)");
        return result.stream()
                .map(this::cleanWaypointName)
                .filter(name -> name.length() >= 2)
                .distinct()
                .toList();
    }

    private void addWaypointMatches(List<String> result, String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
    }

    private String cleanWaypointName(String value) {
        return value == null ? "" : value
                .replaceAll("(가는길에|중간에|그리고|다음|먼저|코스에|추가|포함|해서|하고|으로|로)$", "")
                .replaceAll("(난이도|쉬운|보통|어려운|상급|초보|키로|킬로|km|KM|이하|미만|정도)", "")
                .trim();
    }

    private Integer targetDifficulty(String text) {
        if (containsAny(text, "난이도1", "1단계", "쉬운", "쉽게", "초보", "편한", "낮은난이도")) {
            return 1;
        }
        if (containsAny(text, "난이도2", "2단계", "보통", "일반")) {
            return 2;
        }
        if (containsAny(text, "난이도3", "3단계", "어려운", "숙련")) {
            return 3;
        }
        if (containsAny(text, "난이도4", "4단계", "상급", "전문가", "힘든", "빡센", "도전")) {
            return 4;
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

    private Map<String, Double> weightsFor(
            Integer targetDifficulty,
            Boolean avoidConstruction,
            Boolean avoidSteps,
            Boolean avoidIce
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
        if (Boolean.TRUE.equals(avoidConstruction) || Boolean.TRUE.equals(avoidSteps) || Boolean.TRUE.equals(avoidIce)) {
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
                        || result.getAvoidIce() != null
                        || result.getMaxDistanceKm() != null
        );
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
