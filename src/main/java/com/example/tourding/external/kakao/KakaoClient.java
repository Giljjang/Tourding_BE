package com.example.tourding.external.kakao;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor

public class KakaoClient {
    private final RestTemplate restTemplate;

    @Value("${kakao.client.kakaoAK}")
    private String kakaoAK;

    public KakaoSearchResponse kakaoSearchByLocation(String x, String y, String radius, String query) {
        return kakaoSearch(createUrl(x,y,radius,query));
    }

    public KakaoSearchResponse kakoSearchByName(String query) {
        return kakaoSearch(createUrl(query));
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

        if (x != null) url.append("&x=").append(encode(x));
        if (y != null) url.append("&y=").append(encode(y));
        if (radius != null) url.append("&radius=").append(encode(radius));
        if (query != null) url.append("&query=").append(encode(query));
        return url.toString();
    }

    private String createUrl(String query) {
        return createUrl(null, null, null, query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}
