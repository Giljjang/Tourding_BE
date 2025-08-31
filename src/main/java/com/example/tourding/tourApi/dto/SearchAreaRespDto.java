package com.example.tourding.tourApi.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder

public class SearchAreaRespDto {
    // tourApi에서 키워드 검색조회 /searchKeyword2 주소로 API 호출할 떄 사용
    private String title; // 장소 이름
    private String addr1; // 장소 주소
    private String typeCode; // 관광지 코드
    private String contentid; // 장소 고유 id
    private String contenttypeid; // 장소 고유 카테고리 id
    private String firstimage; // 장소 이미지1
    private String firstimage2; // 장소 이미지2
    private String mapx; // 장소 위도
    private String mapy; // 장소 경도

}
