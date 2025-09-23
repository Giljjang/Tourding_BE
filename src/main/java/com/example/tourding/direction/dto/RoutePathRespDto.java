package com.example.tourding.direction.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RoutePathRespDto {
    private Integer sequenceNum; // path 진행 순서 Open API Response에는 없는거임
    private String lon; // 경도
    private String lat; // 위도

    public static RoutePathRespDto from(List<String> location, int sequenceNum) {
        return RoutePathRespDto.builder()
                .lon(location.get(0))
                .lat(location.get(1))
                .sequenceNum(sequenceNum)
                .build();
    }
}
