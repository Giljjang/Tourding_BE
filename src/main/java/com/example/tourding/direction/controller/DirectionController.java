package com.example.tourding.direction.controller;

import com.example.tourding.direction.dto.DirectionRequestDto;
import com.example.tourding.direction.dto.DirectionResponseDTO;
import com.example.tourding.direction.service.DirectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor

public class DirectionController {

    private final DirectionService directionService;

    @PostMapping
    public DirectionResponseDTO getDirection(@RequestBody DirectionRequestDto requestDto) {
        return directionService.getRoute(requestDto.getStart(), requestDto.getGoal());
    }
}
