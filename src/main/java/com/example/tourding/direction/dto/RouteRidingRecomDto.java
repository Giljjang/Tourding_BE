package com.example.tourding.direction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

@Getter
@Service
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RouteRidingRecomDto {
    private String arrival;      // 도착점
    private String description;  // 소개
    private String minutes;      // 소요분
    private String hours;        // 소요시간
    private String departure;    // 출발점
    private String courseType;   // 코스구분명
    private String courseName;   // 코스명
}
