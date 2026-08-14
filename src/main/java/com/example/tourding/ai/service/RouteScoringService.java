package com.example.tourding.ai.service;

import com.example.tourding.external.open_routes_service.ORSJsonResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class RouteScoringService {
    private static final Set<Integer> PAVED_SURFACES = Set.of(1, 3, 4, 8, 14, 18);
    private static final Set<Integer> POSITIVE_WAYTYPES = Set.of(4, 5, 6);
    private static final Set<Integer> NEGATIVE_WAYTYPES = Set.of(1, 2);

    public ScoreResult score(ORSJsonResponse.Route route, Map<String, Double> weights) {
        double comfort = weightedSuitability(route);
        double flatness = flatness(route);
        double surface = summaryAmount(route, "surface", PAVED_SURFACES::contains);
        double waytype = waytype(route);
        double efficiency = efficiency(route);

        double total = comfort * weight(weights, "comfort")
                + flatness * weight(weights, "flatness")
                + surface * weight(weights, "surface")
                + waytype * weight(weights, "waytype")
                + efficiency * weight(weights, "efficiency");

        return new ScoreResult(
                round(total),
                round(comfort),
                round(flatness),
                round(surface),
                round(waytype),
                round(efficiency)
        );
    }

    private double weightedSuitability(ORSJsonResponse.Route route) {
        ORSJsonResponse.ExtraInfo extra = route.getExtras() == null ? null : route.getExtras().get("suitability");
        if (extra == null || extra.getSummary() == null) {
            return 0.0;
        }
        return extra.getSummary().stream()
                .mapToDouble(row -> (row.getValue() / 10.0) * (row.getAmount() / 100.0))
                .sum();
    }

    private double flatness(ORSJsonResponse.Route route) {
        double severe = summaryAmount(route, "steepness", value -> Math.abs(value) >= 3);
        double uphill = summaryAmount(route, "steepness", value -> value >= 2);
        return clamp(1.0 - severe - (0.5 * uphill));
    }

    private double waytype(ORSJsonResponse.Route route) {
        double positive = summaryAmount(route, "waytype", POSITIVE_WAYTYPES::contains);
        double negative = summaryAmount(route, "waytype", NEGATIVE_WAYTYPES::contains);
        return clamp(0.5 + positive - negative);
    }

    private double efficiency(ORSJsonResponse.Route route) {
        if (route.getSegments() == null || route.getSegments().isEmpty()) {
            return 0.0;
        }
        double detour = route.getSegments().stream()
                .mapToDouble(segment -> segment.getDetourfactor() * (segment.getPercentage() / 100.0))
                .sum();
        double speed = route.getSegments().stream()
                .mapToDouble(segment -> segment.getAvgspeed() * (segment.getPercentage() / 100.0))
                .sum();

        if (detour <= 0) {
            detour = 1.0;
        }
        return clamp((speed / 25.0) * 0.6 + (1.0 / detour) * 0.4);
    }

    private double summaryAmount(ORSJsonResponse.Route route, String key, IntPredicate predicate) {
        ORSJsonResponse.ExtraInfo extra = route.getExtras() == null ? null : route.getExtras().get(key);
        if (extra == null || extra.getSummary() == null) {
            return 0.0;
        }
        return extra.getSummary().stream()
                .filter(row -> predicate.test((int) row.getValue()))
                .mapToDouble(row -> row.getAmount() / 100.0)
                .sum();
    }

    private double weight(Map<String, Double> weights, String key) {
        if (weights == null) {
            return switch (key) {
                case "comfort" -> 0.25;
                case "flatness" -> 0.25;
                case "surface" -> 0.20;
                case "waytype" -> 0.20;
                case "efficiency" -> 0.10;
                default -> 0.0;
            };
        }
        return weights.getOrDefault(key, 0.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    public record ScoreResult(
            double total,
            double comfort,
            double flatness,
            double surface,
            double waytype,
            double efficiency
    ) {
    }

    private interface IntPredicate {
        boolean test(int value);
    }
}
