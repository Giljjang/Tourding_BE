package com.example.tourding.external.riding_course;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Random;

@Component
@RequiredArgsConstructor

public class RidingCourseClient {
    private final RestTemplate restTemplate;

    @Value("${tour.client.serviceKey}")
    private String serviceKey;


    public RidingCourseResponse getRidingCourse(int pagenum) { // pageNum : 0 -> 랜덤페이지 생성, pageNum > 0 사용자 지정 페이지 번호
        try {
            int randomNum = new Random().nextInt(8) + 1; // 만약 pageNum이 0이 들어오면 랜덤 숫자로 페이징
            int pageToUse = (pagenum < 1) ? randomNum : pagenum;
            String safeServiceKey = serviceKey.replace("+", "%2B");

            final String url = "https://api.odcloud.kr/api/15074362/v1/uddi:de938043-9d37-451b-b915-87d7eec667c1"
                    + "?returnType=json"
                    + "&perPage=10"
                    + "&page=" + pageToUse
                    + "&serviceKey=" + safeServiceKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<RidingCourseResponse> response = restTemplate.exchange(java.net.URI.create(url), HttpMethod.GET, entity, RidingCourseResponse.class);

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("라이딩 추천코스 호출 실패"+e);
        }
    }
}
