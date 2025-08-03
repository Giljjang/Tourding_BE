package com.example.tourding.direction.controller;

import com.example.tourding.direction.dto.*;
import com.example.tourding.direction.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor

public class RouteController {

    private final RouteService routeService;

    @PostMapping
    public RouteSummaryRespDto getDirection(@RequestBody RouteRequestDto requestDto) {
        return routeService.getRoute(requestDto.getUserId(), requestDto.getStart(), requestDto.getGoal());
    }

    @GetMapping("/guide")
    public List<RouteGuideRespDto> getGuide(@RequestParam Long userId) {
        return routeService.getGuideByUserId(userId);
    }

    @GetMapping("/path")
    public List<RoutePathRespDto> getPath(@RequestParam Long userId) {
        return routeService.getPathByUserId(userId);
    }

    @GetMapping("/section")
    public List<RouteSectionRespDto> getSection(@RequestParam Long userId) {
        return routeService.getSectionByUserId(userId);
    }

}
