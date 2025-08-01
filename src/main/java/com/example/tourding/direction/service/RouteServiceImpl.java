package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.RouteResponseDto;

public interface RouteServiceImpl {
    RouteResponseDto getRoute(String start, String goal);
}
