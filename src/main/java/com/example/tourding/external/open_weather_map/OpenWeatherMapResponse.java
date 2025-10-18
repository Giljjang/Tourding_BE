package com.example.tourding.external.open_weather_map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class OpenWeatherMapResponse {

    private Coord coord; // 위치 정보 (경도, 위도)
    private List<Weather> weather; // 날씨 정보 리스트 (여러 날씨 상태를 포함)
    private String base; // 내부 매개변수 (서버 기본값)

    private Main main; // 기온, 기압, 습도 등 주요 기상 데이터

    private int visibility; // 시정 (단위: 미터) — 최대 10km
    private Wind wind; // 풍속, 풍향, 돌풍 관련 정보
    private Rain rain; // 강수량 정보 (사용 가능한 경우, 단위: mm/h)
    private Snow snow; // 적설량 정보
    private Clouds clouds; // 구름 정보 (흐림 정도, %)

    private long dt; // 데이터 계산 시간 (UNIX 타임스탬프, UTC)
    private Sys sys; // 시스템 정보 (국가 코드, 일출·일몰 시간 등)

    private int timezone; // UTC로부터의 시간대 차이 (초 단위)

    private int id; // 도시 ID
    private String name; // 도시 이름

    private int cod; // 내부 매개변수 (상태 코드)

    /** 위치 정보 */
    @Data
    public static class Coord {
        private double lon; // 위치의 경도
        private double lat; // 위치의 위도
    }

    /** 날씨 정보 */
    @Data
    public static class Weather {
        private int id; // 날씨 상태 ID
        private String main; // 날씨 그룹 (비, 눈, 구름 등)
        private String description; // 그룹 내 상세한 날씨 설명 (선택한 언어로 제공)
        private String icon; // 날씨 아이콘 ID
    }

    /** 주요 기상 데이터 */
    @Data
    public static class Main {
        private double temp; // 현재 기온 (기본 단위: 켈빈 / metric: 섭씨 / imperial: 화씨)
        private int pressure; // 대기압 (기본: hPa)
        private int humidity; // 습도 (%)

        @JsonProperty("feels_like")
        private double feelsLike; // 체감 온도

        @JsonProperty("temp_min")
        private double tempMin; // 현재 최저 기온 (도시 내 관측된 최저값)

        @JsonProperty("temp_max")
        private double tempMax; // 현재 최고 기온 (도시 내 관측된 최고값)

        @JsonProperty("sea_level")
        private int seaLevel; // 해수면의 대기압 (단위: hPa)

        @JsonProperty("grnd_level")
        private int grndLevel; // 지상(지표)의 대기압 (단위: hPa)
    }

    /** 풍속 및 풍향 */
    @Data
    public static class Wind {
        private double speed; // 풍속 (단위 기본: m/s, imperial: mph)
        private int deg; // 풍향 (도 단위, 기상학적)
        private double gust; // 돌풍 속도 (단위: m/s)
    }

    /** 강수량 */
    @Data
    public static class Rain {
        @JsonProperty("1h")
        private double rainData; // 1시간 동안의 강수량 (mm/h, 사용 가능한 경우)
    }

    /** 적설량 */
    @Data
    public static class Snow {
        @JsonProperty("1h")
        private int snowData; // 눈의 양
    }

    /** 구름량 */
    @Data
    public static class Clouds {
        private int all; // 구름의 양 (흐림 정도, %)
    }

    /** 시스템 정보 */
    @Data
    public static class Sys {
        private String country; // 국가 코드 (예: KR, JP, US)
        private long sunrise; // 일출 시간 (UNIX 타임스탬프, UTC)
        private long sunset; // 일몰 시간 (UNIX 타임스탬프, UTC)
    }
}