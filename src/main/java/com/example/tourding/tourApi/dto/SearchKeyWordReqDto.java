package com.example.tourding.tourApi.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder

public class SearchKeyWordReqDto {
    private String keyword;
    private int pageNum;
    private String typeCode;
    private int areaCode;
}
