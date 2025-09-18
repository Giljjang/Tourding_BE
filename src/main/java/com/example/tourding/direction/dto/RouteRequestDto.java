package com.example.tourding.direction.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RouteRequestDto {
    private Long userId;
    private String start;
    private String goal;
    private String wayPoints;
    private String locateName; // 출발지, 경유지, 도착지의 이름이 들어감
    private String typeCode; // 출발지, 경유지, 도착지 가 들어감
}
