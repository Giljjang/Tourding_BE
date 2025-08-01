package com.example.tourding.direction.external;

import com.example.tourding.direction.dto.RouteResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor

public class NaverMapClient {
    private final RestTemplate restTemplate;

    @Value("${naver.client.id}")
    private String clientId;

    @Value("${naver.client.secret}")
    private String clientSecret;

    public RouteResponseDto getDirection(String start, String goal) {
        String url = "https://naveropenapi.apigw.ntruss.com/map-direction/v1" +
                "?start=" + start + "&goal=" + goal;

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ncp-apigw-api-key-id", clientId);
        headers.set("x-ncp-apigw-api-secret", clientSecret);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<RouteResponseDto> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, RouteResponseDto.class);

        return response.getBody();
    }
}
