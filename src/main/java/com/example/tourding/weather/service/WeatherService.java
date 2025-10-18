package com.example.tourding.weather.service;

import com.example.tourding.external.open_weather_map.OpenWeatherMapClient;
import com.example.tourding.external.open_weather_map.OpenWeatherMapResponse;
import com.example.tourding.weather.dto.WeatherReqDto;
import com.example.tourding.weather.dto.WeatherRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class WeatherService {

    private final OpenWeatherMapClient openWeatherMapClient;

    public WeatherRespDto getWeather(WeatherReqDto weatherReqDto) {
        String lat = weatherReqDto.getLat();
        String lon = weatherReqDto.getLon();

        OpenWeatherMapResponse openWeatherMapResponse = openWeatherMapClient.getWeather(lat,lon);

        return WeatherRespDto.from(openWeatherMapResponse);
    }
}
