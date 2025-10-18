package com.example.tourding.direction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class RouteLocationNameRespDto {
    private Integer sequenceNum; // 순서 보존용
    private String name;
    private String type;
    private String typeCode;
    private String contentId;
    private String contentTypeId;
    private String lon;
    private String lat;
}
