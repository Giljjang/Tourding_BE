package com.example.tourding.kakaoSearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KakaoSearchReqDto {
    private String lon; // 경도
    private String lat; // 위도
    private String radius;
}
