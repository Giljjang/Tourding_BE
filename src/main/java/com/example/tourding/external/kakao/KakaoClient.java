package com.example.tourding.external.kakao;

import com.example.tourding.enums.RouteApiCode;
import com.example.tourding.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor

public class KakaoClient {
    private final RestTemplate restTemplate;

    @Value("${kakao.client.kakaoAK}")
    private String kakaoAK;

    public KakaoSearchResponse kakaoSearchConvenience(String x, String y, String radius, String query) {
        try {

            return kakaoSearch(x, y, radius, query);

        } catch (CustomException exception) {
            throw exception;
        }
    }

    private KakaoSearchResponse kakaoSearch(String x, String y, String radius, String query) {
        final String url = "https://dapi.kakao.com/v2/local/search/keyword.json" +
                "?query=" + query + "&x=" + x + "&y=" + y + "&radius=" + radius;
        System.out.println(url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoAK);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<KakaoSearchResponse> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, KakaoSearchResponse.class);

        return response.getBody();
    }

    private KakaoSearchResponse combineResponses(KakaoSearchResponse response1, KakaoSearchResponse response2) {
        List<KakaoSearchResponse.Document> combinedDocuments = new ArrayList<>();

        if (response1.getDocuments() != null) {
            combinedDocuments.addAll(response1.getDocuments());
        }

        if (response2.getDocuments() != null) {
            combinedDocuments.addAll(response2.getDocuments());
        }

        KakaoSearchResponse combinedResponse = new KakaoSearchResponse();
        combinedResponse.setDocuments(combinedDocuments);

        return combinedResponse;
    }

}
