package com.example.tourding.external.open_routes_service;

import com.example.tourding.direction.dto.RouteOptionDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@RequiredArgsConstructor

public class ORSCilent {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${open.route.service.key}")
    private String routeServiceKey;

    @Value("${open.route.service.base-url:https://api.openrouteservice.org}")
    private String routeServiceBaseUrl;

    public ORSResponse getORSDirection(String start, String goal, String wayPoints) {
        return getORSDirection(start, goal, wayPoints, RouteOptionDto.defaults());
    }

    public ORSResponse getORSDirection(String start, String goal, String wayPoints, RouteOptionDto routeOption) {
        try {
            RouteOptionDto resolvedOption = routeOption == null ? RouteOptionDto.defaults() : routeOption;
            String profile = resolvedOption.getCyclingProfile() == null || resolvedOption.getCyclingProfile().isBlank()
                    ? "cycling-regular"
                    : resolvedOption.getCyclingProfile();
            final String url = routeServiceBaseUrl + "/v2/directions/" + profile + "/geojson";

            List<List<Double>> coordinates = new ArrayList<>();
            String[] startCoords = start.split(",");
            coordinates.add(List.of(
                    Double.parseDouble(startCoords[0].trim()),
                    Double.parseDouble(startCoords[1].trim())
            ));

            if (wayPoints != null && !wayPoints.isEmpty()) {
                String[] wayPointsArray = wayPoints.split("\\|");
                for (String wayPoint : wayPointsArray) {
                    String[] wayPointCoords = wayPoint.split(",");
                    coordinates.add(List.of(
                            Double.parseDouble(wayPointCoords[0].trim()),
                            Double.parseDouble(wayPointCoords[1].trim())
                    ));
                }
            }

            String[] goalCoords = goal.split(",");
            coordinates.add(List.of(
                    Double.parseDouble(goalCoords[0].trim()),
                    Double.parseDouble(goalCoords[1].trim())
            ));

            Map<String, Object> body = new HashMap<>();
            body.put("coordinates", coordinates);
            body.put("preference", Boolean.TRUE.equals(resolvedOption.getFastRoute()) ? "fastest" : "recommended");
            body.put("elevation", true);
            body.put("instructions", true);
            body.put("maneuvers", true);
            body.put("geometry", true);
            body.put("geometry_simplify", false);
            body.put("extra_info", List.of("steepness", "suitability", "surface", "waytype"));
            body.put("attributes", List.of("avgspeed", "detourfactor", "percentage"));
            body.put("options", buildOptions(resolvedOption));

            String requestBody = objectMapper.writeValueAsString(body);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", routeServiceKey);

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<ORSResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ORSResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("OpenRouteService 호출 실패", e);
        }
    }

    public ORSJsonResponse getRouteAnalysis(ORSRouteAnalysisRequest request) {
        try {
            String profile = request.getProfile() == null ? "cycling-regular" : request.getProfile();
            final String url = routeServiceBaseUrl + "/v2/directions/" + profile + "/json";

            String requestBody = objectMapper.writeValueAsString(request.toRequestBody());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", routeServiceKey);

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<ORSJsonResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ORSJsonResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("OpenRouteService 분석 호출 실패", e);
        }
    }

    private Map<String, Object> buildOptions(RouteOptionDto option) {
        List<String> avoidFeatures = new ArrayList<>();
        if (Boolean.TRUE.equals(option.getAvoidSteps())) {
            avoidFeatures.add("steps");
        }
        if (Boolean.TRUE.equals(option.getAvoidFords())) {
            avoidFeatures.add("fords");
        }

        return Map.of(
                "avoid_features", avoidFeatures,
                "profile_params", Map.of(
                        "weightings", Map.of("steepness_difficulty", steepnessDifficulty(option.getSkillLevel()))
                )
        );
    }

    private int steepnessDifficulty(String skillLevel) {
        if ("NORMAL".equals(skillLevel)) {
            return 1;
        }
        if ("ADVANCED".equals(skillLevel)) {
            return 2;
        }
        if ("PRO".equals(skillLevel)) {
            return 3;
        }
        return 0;
    }
}
