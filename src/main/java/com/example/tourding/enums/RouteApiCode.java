package com.example.tourding.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor

public enum RouteApiCode {
    SUCCESS(0, "성공"),
    SAME_START_AND_GOAL(1, "출발지와 도착지가 동일"),
    OUT_OF_ROAD(2, "출발지 또는 도착지가 도로 주변이 아님"),
    ROUTE_UNAVAILABLE(3, "자동차 길찾기 결과 제공 불가"),
    WAYPOINT_OUT_OF_ROAD(4, "경유지가 도로 주변이 아님"),
    TOO_LONG_DISTANCE(5, "경유지를 포함한 직선거리 합이 1500km 초과"),
    APPLE_WITHDRAW_FAILED(6,"애플 access token 요청 실패");

    private final int code;
    private final String message;

    public static RouteApiCode fromCode(int code) {
        for (RouteApiCode value : values()) {
            if (value.code == code) return value;
        }
        throw new IllegalArgumentException("알 수 없는 응답 코드: " + code);
    }
}
