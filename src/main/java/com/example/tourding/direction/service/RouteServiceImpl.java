package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.RouteSummaryRespDto;
import com.example.tourding.user.entity.User;

public interface RouteServiceImpl {
    RouteSummaryRespDto getRoute(Long userId, String start, String goal);
}
