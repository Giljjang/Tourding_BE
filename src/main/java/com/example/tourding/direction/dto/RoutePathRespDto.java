package com.example.tourding.direction.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RoutePathRespDto {
    private Integer sequenceNum; // path 진행 순서 Open API Response에는 없는거임
    private Double lon; // 경도
    private Double lat; // 위도
}
