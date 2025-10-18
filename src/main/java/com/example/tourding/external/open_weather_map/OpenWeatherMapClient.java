package com.example.tourding.external.open_weather_map;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor

public class OpenWeatherMapClient {
    private final RestTemplate restTemplate;

    @Value("${open.weather.map.key}")
    private String key;

    public OpenWeatherMapResponse getWeather(String lat, String lon) {
        try {
            final String url = "https://api.openweathermap.org/data/2.5/weather?&lang=kr&"
                    + "lat=" + lat
                    + "&lon=" + lon
                    + "&appid=" + key;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<OpenWeatherMapResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    OpenWeatherMapResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("WeatherRespDto 호출 실패",e);
        }
    }
}
