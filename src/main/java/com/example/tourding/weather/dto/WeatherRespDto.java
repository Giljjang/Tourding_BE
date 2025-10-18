package com.example.tourding.weather.dto;

import com.example.tourding.external.open_weather_map.OpenWeatherMapResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class WeatherRespDto {
    private String weather; // 날씨 그룹 (비, 눈, 구름 등)
    private String description; // 그룹 내 상세한 날씨 설명 (선택한 언어로 제공)
    private double temp; // 현재 기온 (기본 단위: 켈빈 / metric: 섭씨 / imperial: 화씨)
    private Detail detail;
    private Rain rain;
    private Snow snow;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    private static class Detail {
        private int pressure; // 대기압 (기본: hPa)
        private int humidity; // 습도 (%)
        private double feelsLike; // 체감 온도
        private double tempMin; // 현재 최저 기온 (도시 내 관측된 최저값)
        private double tempMax; // 현재 최고 기온 (도시 내 관측된 최고값)
        private double speed; // 풍속 (단위 기본: m/s, imperial: mph)
        private int deg; // 풍향 (도 단위, 기상학적)
        private int cloud; // 구름의 양 (흐림 정도, %)
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    private static class Rain {
        private double rain; // 1시간 동안의 강수량 (mm/h, 사용 가능한 경우)
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    private static class Snow {
        private double snow;
    }

    public static WeatherRespDto from(OpenWeatherMapResponse openWeatherMapResponse) {

        OpenWeatherMapResponse.Main main = openWeatherMapResponse.getMain();
        OpenWeatherMapResponse.Wind wind = openWeatherMapResponse.getWind();
        OpenWeatherMapResponse.Clouds clouds = openWeatherMapResponse.getClouds();
        OpenWeatherMapResponse.Rain rainData = openWeatherMapResponse.getRain();
        OpenWeatherMapResponse.Snow snowData = openWeatherMapResponse.getSnow();

        Detail detail = Detail.builder()
                .pressure(main.getPressure())
                .humidity(main.getHumidity())
                .feelsLike(Math.round((main.getFeelsLike()-273)*10.0) / 10.0)
                .tempMin(Math.round((main.getTempMin()-273)*10.0) / 10.0)
                .tempMax(Math.round((main.getTempMax()-273)*10.0) / 10.0)
                .speed(wind.getSpeed())
                .deg(wind.getDeg())
                .cloud(clouds.getAll())
                .build();

        Rain rain = (rainData != null) ? Rain.builder().rain(rainData.getRainData()).build() :
                null;

        Snow snow = (rainData != null) ? Snow.builder().snow(snowData.getSnowData()).build() :
                null;

        return WeatherRespDto.builder()
                .weather(openWeatherMapResponse.getWeather().get(0).getMain())
                .description(openWeatherMapResponse.getWeather().get(0).getDescription())
                .temp(Math.round((openWeatherMapResponse.getMain().getTemp()-273)*10.0) / 10.0)
                .detail(detail)
                .rain(rain)
                .snow(snow)
                .build();
    }
}
