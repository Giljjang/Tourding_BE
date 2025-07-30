package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.DirectionResponseDTO;

public interface DirectionServiceImpl {
    DirectionResponseDTO getRoute(String start, String goal);
}
