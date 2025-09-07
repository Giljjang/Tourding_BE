package com.example.tourding.kakaoSearch.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class KakaoSearchRespDto {
    private String name;
    private String lon; // 경도 (x)
    private String lat; // 위도 (y)
}
