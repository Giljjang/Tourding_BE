package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.RouteResponseDto;
import com.example.tourding.direction.external.NaverMapClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class RouteService implements RouteServiceImpl {
    private final NaverMapClient naverMapClient;

    @Override
    public RouteResponseDto getRoute(String start, String end) {
        RouteResponseDto responseDTO = naverMapClient.getDirection(start, end);
        return new RouteResponseDto(responseDTO);
    }
}
