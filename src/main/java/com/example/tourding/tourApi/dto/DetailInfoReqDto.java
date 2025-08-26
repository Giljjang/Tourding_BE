package com.example.tourding.tourApi.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder

public class DetailInfoReqDto {
    private String contentid; // 장소 고유 id
    private String contenttypeid; // 장소 고유 카테고리 id
}
