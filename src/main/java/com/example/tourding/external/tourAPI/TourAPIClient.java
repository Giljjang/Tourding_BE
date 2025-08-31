package com.example.tourding.external.tourAPI;

import com.example.tourding.tourApi.dto.SearchLocationDto;
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
        return callApi(url, SearchAreaResponse.class);
    }

    public SearchAreaResponse searchByCategory(int pageNum, String typeCode, int areaCode) {
        String url = createUrl("/areaBasedList2", pageNum, typeCode, areaCode);
        return callApi(url, SearchAreaResponse.class);
    }

    public SearchAreaResponse searchLocationDto(int pageNum, String mapX, String mapY, String radius, String typeCode) {
        String url = createUrl("/locationBasedList2", pageNum, mapX, mapY, radius, typeCode);
        return callApi(url, SearchAreaResponse.class);
    }

    public DetailCommonResponse searchDetailCommon(String contentTypeId) {
        String url = createUrl("/detailCommon2", contentTypeId);
        return callApi(url, DetailCommonResponse.class);
    }

    public DetailIntroResponse searchDetailIntro(String contentId, String contentTypeId) {
        String url = createUrl("/detailIntro2", contentId, contentTypeId);
        return callApi(url, DetailIntroResponse.class);
    }

    private String createUrl(String path, int pageNum, String typeCode, int areaCode, String keyWord,
                             String contentId, String contentTypeId, String mapX, String mapY, String radius, String sortType) {
        String safeServiceKey = serviceKey.replace("+", "%2B");
        StringBuilder url = new StringBuilder(baseUrl + path + "?MobileOS=IOS&MobileApp=tourding&_type=json&numOfRows=12");
        url.append("&pageNo=").append(pageNum);

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
        if(contentId != null) {
            url.append("&contentId=").append(contentId);
        }
        if(contentTypeId != null) {
            url.append("&contentTypeId=").append(contentTypeId);
        }
        if(mapX != null && !mapX.isBlank()) {
            url.append("&mapX=").append(mapX);
        }
        if(mapY != null && !mapY.isBlank()) {
            url.append("&mapY=").append(mapY);
        }
        if(radius != null && !radius.isBlank()) {
            url.append("&radius=").append(radius);
        }
        if(sortType != null && !sortType.isBlank()) {
            url.append("&arrange=").append(sortType);
        }

        url.append("&serviceKey=").append(safeServiceKey);
        return url.toString();
    }

    private String createUrl(String path, int pageNum, String typeCode, int areaCode, String keyWord) { // 지역, 카테고리, 검색어 기반 검색 API 주소 생성
        return createUrl(path, pageNum, typeCode, areaCode, keyWord, null, null, null, null,null,null);
    }

    private String createUrl(String path, int pageNum, String typeCode, int areaCode) { // 지역, 카테고리 기반 조회 API 주소 생성
        return createUrl(path, pageNum, typeCode, areaCode, null,null,null,null,null,null,null);
    }

    private String createUrl(String path, String contentId) { // 장소 공통정보 조회 API 생성
        return createUrl(path, 0, null, 0, null, contentId,null,null,null,null,null);
    }
    private String createUrl(String path, String contentId, String contentTypeId) { // 장소 소개정보 조회 API 주소 생성
        return createUrl(path, 0, null, 0, null, contentId, contentTypeId,null,null,null,null);
    }

    private String createUrl(String path, int pageNum, String mapX, String mapY, String radius, String typeCode) { // 사용자 위치기반 검색 API 주소 생성
        return createUrl(path, pageNum, typeCode, 0, null,null,null,mapX,mapY,radius,"E");
    }

    private <T> T callApi(String urlString, Class<T> responseType) {
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
            return jsonMapper.readValue(body, responseType);
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
