package com.example.tourding.external.tourAPI;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor

public class TourAPIClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Value("${tour.client.serviceKey}")
    private String serviceKey;

    private static final String baseUrl = "https://apis.data.go.kr/B551011/KorService2";

    public SearchKeyWordResponse searchKeyWord(String keyword, int pageNum) {
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String safeServiceKey = serviceKey.replace("+", "%2B");

        String urlString = baseUrl + "/searchKeyword2?MobileOS=IOS&MobileApp=tourding&_type=json&arrange=A"
                + "&pageNo=" + pageNum
                + "&keyword=" + encodedKeyword
                + "&serviceKey=" + safeServiceKey;

        URI url = URI.create(urlString);

        HttpHeaders headers = new HttpHeaders();
        headers.set("accept", "*/*");
        headers.set("User-Agent", "curl/7.88.1");
        headers.set("Connection", "keep-alive");

        System.out.println(url);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> rawResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        String body = rawResponse.getBody();
        String contentType = rawResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

        jsonMapper.coercionConfigFor(LogicalType.POJO)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);

        // 디버깅용
        System.out.println("[DEBUG] 응답 상태 코드: " + rawResponse.getStatusCode());
        System.out.println("[DEBUG] 응답 헤더: " + rawResponse.getHeaders());
        System.out.println("[DEBUG] Content-Type: " + contentType);
        System.out.println("[DEBUG] Body preview: " + (body != null ? body.substring(0, Math.min(body.length(), 500)) : "null"));


        if (body == null || body.isBlank()) {
            throw new IllegalStateException("API 응답이 비어있음");
        }

        // XML 에러 감지: contentType에 xml 포함되거나 본문이 < 로 시작하면
        if ((contentType != null && contentType.contains("xml")) || body.trim().startsWith("<")) {
            if (body.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")) {
                throw new IllegalStateException("서비스 키 인증 실패: SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
            }
            // 다른 XML 에러면 메시지 추출
            throw new IllegalStateException("XML 에러 응답: " + extractSimpleErrorMessage(body));
        }

        // JSON이면 파싱
        try {
            return jsonMapper.readValue(body, SearchKeyWordResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON 파싱 실패: " + e.getMessage() + " / body: " + body, e);
        }

    }

    private String extractSimpleErrorMessage(String xml) {
        String marker = "<returnAuthMsg>";
        if (xml.contains(marker)) {
            int start = xml.indexOf(marker) + marker.length();
            int end = xml.indexOf("</returnAuthMsg>", start);
            if (end > start) {
                return xml.substring(start, end);
            }
        }
        return "알 수 없는 XML 에러";
    }
}
