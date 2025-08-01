package com.example.tourding.direction.controller;

import com.example.tourding.direction.dto.RouteRequestDto;
import com.example.tourding.direction.dto.RouteResponseDto;
import com.example.tourding.direction.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor

public class RouteController {

    private final RouteService routeService;

    @PostMapping
    public RouteResponseDto getDirection(@RequestBody RouteRequestDto requestDto) {
        return routeService.getRoute(requestDto.getStart(), requestDto.getGoal());
    }
}
