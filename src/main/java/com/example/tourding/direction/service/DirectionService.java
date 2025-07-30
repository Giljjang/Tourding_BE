package com.example.tourding.direction.service;

import com.example.tourding.direction.dto.DirectionResponseDTO;
import com.example.tourding.direction.external.NaverMapClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class DirectionService implements DirectionServiceImpl {
    private final NaverMapClient naverMapClient;

    @Override
    public DirectionResponseDTO getRoute(String start, String end) {
        DirectionResponseDTO responseDTO = naverMapClient.getDirection(start, end);
        return new DirectionResponseDTO(responseDTO);
    }
}
