package com.example.tourding.external.naver;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
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
        private List<List<String>> path;
        private List<Section> section;
    }

    @Getter
    public static class Summary {
        private List<List<String>> bbox;
        private String departureTime;
        private int distance;
        private int duration;
        private int fuelPrice;
        private int taxiFare;
        private int tollFare;
        private StartOrGoal start;
        private Goal goal;
    }

    @Getter
    public static class StartOrGoal {
        private List<String> location;
    }

    @Getter
    public static class Goal extends StartOrGoal {
        private int dir;  // 진행방향 정보
    }

    @Getter
    public static class Guide {
        private int distance;
        private int duration;
        private String instructions;
        private String lon; // 경도
        private String lat; // 위도
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

