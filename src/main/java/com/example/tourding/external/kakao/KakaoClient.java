package com.example.tourding.external.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Component
@RequiredArgsConstructor

public class KakaoClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kakao.client.kakaoAK}")
    private String kakaoAK;

    public KakaoSearchResponse kakaoSearchByLocation(String x, String y, String radius, String query) {
        return kakaoSearch(createUrl(x,y,radius,query));
    }

    public KakaoSearchResponse kakoSearchByName(String query) {
        return kakaoSearch(createUrl(query));
    }

    public Optional<String> kakaoAddressNameByCoordinate(String x, String y) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoAK);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    createCoord2AddressUrl(x, y),
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JsonNode documents = objectMapper.readTree(response.getBody()).path("documents");
            if (!documents.isArray() || documents.isEmpty()) {
                return Optional.empty();
            }
            JsonNode document = documents.get(0);
            String roadAddress = document.path("road_address").path("address_name").asText("");
            if (!roadAddress.isBlank()) {
                return Optional.of(roadAddress);
            }
            String address = document.path("address").path("address_name").asText("");
            return address.isBlank() ? Optional.empty() : Optional.of(address);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private KakaoSearchResponse kakaoSearch(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoAK);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<KakaoSearchResponse> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, KakaoSearchResponse.class);

        return response.getBody();
    }

    private String createUrl(String x, String y, String radius, String query) {
        StringBuilder url = new StringBuilder("https://dapi.kakao.com/v2/local/search/keyword.json?");

        if (x != null) url.append("&x=").append(x);
        if (y != null) url.append("&y=").append(y);
        if (radius != null) url.append("&radius=").append(radius);
        if (query != null) url.append("&query=").append(query);
        return url.toString();
    }

    private String createUrl(String query) {
        return createUrl(null, null, null, query);
    }

    private String createCoord2AddressUrl(String x, String y) {
        return "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=" + x + "&y=" + y;
    }

}
