package com.example.tourding.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor

public enum ErrorCode{
    SUCCESS(0, "성공",HttpStatus.OK),
    SAME_START_AND_GOAL(1, "출발지와 도착지가 동일",HttpStatus.BAD_REQUEST),
    OUT_OF_ROAD(2, "출발지 또는 도착지가 도로 주변이 아님",HttpStatus.BAD_REQUEST),
    ROUTE_UNAVAILABLE(3, "자동차 길찾기 결과 제공 불가",HttpStatus.BAD_REQUEST),
    WAYPOINT_OUT_OF_ROAD(4, "경유지가 도로 주변이 아님",HttpStatus.BAD_REQUEST),
    TOO_LONG_DISTANCE(5, "경유지를 포함한 직선거리 합이 1500km 초과",HttpStatus.BAD_REQUEST),
    APPLE_WITHDRAW_FAILED(6,"애플 access token 요청 실패",HttpStatus.FORBIDDEN),
    DUPLICATE_EMAIL(7,"이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT);

    @Getter
    private final int code;

    @Getter
    private final String message;
    private final HttpStatus httpStatus;

    public static ErrorCode fromCode(int code) {
        for (ErrorCode value : values()) {
            if (value.code == code) return value;
        }
        throw new IllegalArgumentException("알 수 없는 응답 코드: " + code);
    }

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.httpStatus = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return httpStatus;
    }
}
