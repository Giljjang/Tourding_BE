package com.example.tourding.tourApi.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder

public class SearchLocationDto {
    int pageNum;
    String mapX;
    String mapY;
    String radius;
    String typeCode; // 관광지 코드
}
