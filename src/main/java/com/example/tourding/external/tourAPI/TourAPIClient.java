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

    public SearchAreaResponse searchByKeyword(String keyword, int pageNum, String typeCode, int areaCode) {
        String url = createUrl("/searchKeyword2", pageNum, typeCode, areaCode, keyword);
        return callApi(url);
    }

    public SearchAreaResponse searchByCategory(int pageNum, String typeCode, int areaCode) {
        String url = createUrl("/areaBasedList2", pageNum, typeCode, areaCode);
        return callApi(url);
    }

    private String createUrl(String path, int pageNum, String typeCode, int areaCode, String keyWord) {
        String safeServiceKey = serviceKey.replace("+", "%2B");
        StringBuilder url = new StringBuilder(baseUrl + path + "?MobileOS=IOS&MobileApp=tourding&_type=json&arrange=A");
        url.append("&pageNo=").append(pageNum);
        url.append("&serviceKey=").append(safeServiceKey);

        if(areaCode != 0) {
            url.append("&areaCode=").append(areaCode);
        }
        if(typeCode != null && !typeCode.equals("0")) {
            url.append("&cat1=").append(typeCode);
        }
        if(keyWord != null && !keyWord.isBlank()) {
            String encodedKeyword = URLEncoder.encode(keyWord, StandardCharsets.UTF_8);
            url.append("&keyword=").append(encodedKeyword);
        }
        return url.toString();
    }

    private String createUrl(String path, int pageNum, String typeCode, int areaCode) {
        return createUrl(path, pageNum, typeCode, areaCode, null);
    }

    private SearchAreaResponse callApi(String urlString) {
        URI url = URI.create(urlString);
        HttpHeaders headers = new HttpHeaders();
        headers.set("accept", "*/*");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> rawResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String body = rawResponse.getBody();
        String contentType = rawResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

        jsonMapper.coercionConfigFor(LogicalType.POJO)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);

        if (body == null || body.isBlank()) {
            throw new IllegalStateException("API 응답이 비어있음");
        }

        if ((contentType != null && contentType.contains("xml")) || body.trim().startsWith("<")) {
            if (body.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")) {
                throw new IllegalStateException("서비스 키 인증 실패: SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
            }
            throw new IllegalStateException("XML 에러 응답: " + extractSimpleErrorMessage(body));
        }

        try {
            return jsonMapper.readValue(body, SearchAreaResponse.class);
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
