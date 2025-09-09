package com.example.tourding.external.naver;

import com.example.tourding.enums.ErrorCode;
import com.example.tourding.exception.CustomException;
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

    public ApiRouteResponse getDirection(String start, String goal, String wayPoints) {
        try {
            final String url = "https://maps.apigw.ntruss.com/map-direction/v1/driving" +
                    "?option=traavoidcaronly" + "&start=" + start + "&goal=" + goal + "&waypoints=" + wayPoints;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-NCP-APIGW-API-KEY-ID", clientId);
            headers.set("X-NCP-APIGW-API-KEY", clientSecret);

            System.out.println("headers: " + headers);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<ApiRouteResponse> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, ApiRouteResponse.class);

            ApiRouteResponse responseBody = response.getBody();
            ErrorCode code = ErrorCode.fromCode(responseBody.getCode());

            if (code != ErrorCode.SUCCESS) {
                throw new CustomException(code);
            }

            return response.getBody();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("길찾기 API 호출 실패");
        }
    }
}
