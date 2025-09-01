package com.example.tourding.direction.dto;

import com.example.tourding.direction.entity.RouteLocationName;
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
    private String lon;
    private String lat;

    public static RouteLocationNameRespDto fromEntity(RouteLocationName routeLocationName, int index) {
        return RouteLocationNameRespDto.builder()
                .name(routeLocationName.getName())
                .type(routeLocationName.getType())
                .lon(routeLocationName.getLon())
                .lat(routeLocationName.getLat())
                .typeCode(routeLocationName.getTypeCode())
                .sequenceNum(index)
                .build();
    }
}
