package com.example.tourding.tourApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class SearchLocationDto {
    int pageNum;
    String mapX;
    String mapY;
    String radius;
    String typeCode; // 관광지 코드
}
