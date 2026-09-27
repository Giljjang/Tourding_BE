package com.example.tourding.external.open_routes_service;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ORSJsonResponse {
    private List<Double> bbox;
    private List<Route> routes;
    private Metadata metadata;

    @Data
    public static class Route {
        private Summary summary;
        private List<Segment> segments;
        private String geometry;
        private List<Integer> way_points;
        private Map<String, ExtraInfo> extras;
    }

    @Data
    public static class Summary {
        private double distance;
        private double duration;
        private double ascent;
        private double descent;
    }

    @Data
    public static class Segment {
        private double distance;
        private double duration;
        private double detourfactor;
        private double percentage;
        private double avgspeed;
        private double ascent;
        private double descent;
        private List<Step> steps;
    }

    @Data
    public static class Step {
        private double distance;
        private double duration;
        private int type;
        private String instruction;
        private String name;
        private List<Integer> way_points;
    }

    @Data
    public static class ExtraInfo {
        private List<List<Double>> values;
        private List<ExtraSummary> summary;
    }

    @Data
    public static class ExtraSummary {
        private double value;
        private double distance;
        private double amount;
    }

    @Data
    public static class Metadata {
        private String attribution;
        private String service;
        private long timestamp;
        private Map<String, Object> query;
        private Map<String, Object> engine;
    }
}
