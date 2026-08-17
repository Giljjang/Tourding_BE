package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.RouteRequestDto;
import com.example.tourding.direction.dto.RouteGuideRespDto;

public interface RouteServiceImpl {
    RouteGuideRespDto getRoute(RouteRequestDto requestDto);
}
