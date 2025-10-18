package com.example.tourding.weather.controller;

import com.example.tourding.weather.dto.WeatherReqDto;
import com.example.tourding.weather.dto.WeatherRespDto;
import com.example.tourding.weather.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/weather")
public class WeatherController {

    private final WeatherService weatherService;

    @Operation(summary = "날씨 데이터 호출", description = "경도, 위도를 입력해서 날시 데이터를 받습니다.")
    @PostMapping
    public ResponseEntity<WeatherRespDto> getWeather(@RequestBody WeatherReqDto weatherReqDto) {
        return ResponseEntity.ok(weatherService.getWeather(weatherReqDto));
    }
}
