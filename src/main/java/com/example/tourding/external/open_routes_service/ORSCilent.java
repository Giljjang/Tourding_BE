package com.example.tourding.external.open_routes_service;

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

    public ORSResponse getORSDirection(String start, String goal, String wayPoints) {
        try {
            final String url = "https://api.openrouteservice.org/v2/directions/cycling-regular/geojson";

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
}
