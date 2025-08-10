package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.RouteRequestDto;
import com.example.tourding.direction.dto.RouteSummaryRespDto;

public interface RouteServiceImpl {
    RouteSummaryRespDto getRoute(RouteRequestDto requestDto);
}
