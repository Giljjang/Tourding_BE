package com.example.tourding.direction.external;

import lombok.Getter;

import java.util.List;

@Getter

public class ApiRouteResponse {
    private int code;
    private String message;
    private String currentDateTime;
    private Route route;

    @Getter
    public static class Route {
        private List<Traoptimal> traoptimal;
    }

    @Getter
    public static class Traoptimal {
        private Summary summary;
        private List<Guide> guide;
        private List<List<Double>> path;
        private List<Section> section;
    }

    @Getter
    public static class Summary {
        private String departureTime;
        private int distance;
        private int duration;
        private int fuelPrice;
        private int taxiFare;
        private Double startLon; // 시작지점 경도
        private Double startLat; // 시작지점 위도
        private Double goalLon; // 도착지점 경도
        private Double goalLat; // 도착지점 위도
        private Integer goalDir; // 경로상 진행방향을 중심으로 설정한 도착지의 위치를 나타낸 숫자 (0: 전방, 1:왼쪽, 2:오른쪽(

        // bbox : 전체 경로 경계 영역
        private Double bboxSwLon; // 왼쪽 아래 경도
        private Double bboxSwLat; // 왼쪽 아래 위도
        private Double bboxNeLon; // 오른쪽 위 경도
        private Double bboxNeLat; // 오른쪽 위 위도
    }

    @Getter
    public static class Guide {
        private int distance;
        private int duration;
        private String instructions;
        private int pointIndex;
        private int type;
    }

    @Getter
    public static class Section {
        private String name;
        private int distance;
        private int pointCount;
        private int pointIndex;
        private int speed;
        private int congestion;
    }
}

