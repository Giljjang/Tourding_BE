package com.example.tourding.direction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class RouteByNameReqDto {
    private Long userId;
    private String start;
    private String goal;
}
