package com.example.tourding.external.open_routes_service;

import lombok.Data;

import java.util.List;

@Data
public class ORSResponse {
    private String type;
    private List<Double> bbox;
    private List<ORSFeatures> features;
    private ORSMetadata metadata;

    @Data
    public static class ORSFeatures {
        private String type;
        private List<Double> bbox;
        private ORSProperties properties;
        private ORSGeometry geometry;
    }

    @Data
    public static class ORSProperties {
        private List<ORSSegment> segments;
        private List<Integer> way_points;
        private ORSSummary summary;
    }

    @Data
    public static class ORSGeometry {
        private String type;
        private List<List<Double>> coordinates;
    }

    @Data
    public static class ORSSummary {
        private double distance; // 총 거리 (m)
        private double duration; // 총 시간 (초)
    }

    @Data
    public static class ORSSegment {
        private double distance;
        private double duration;
        private List<ORSStep> steps;
    }

    @Data
    public static class ORSStep {
        private double distance;
        private double duration;
        private int type;                 // ORS에서 제공하는 turn type 코드
        private String instruction;       // 안내 문구
        private String name;              // 도로/자전거길 이름 (없으면 "-")
        private List<Integer> way_points; // 시작-끝 좌표 인덱스
    }

    @Data
    public static class ORSMetadata {
        private String attribution;
        private String service;
        private long timestamp;
        private ORSQuery query;
        private ORSEngine engine;
    }

    @Data
    public static class ORSQuery {
        private List<List<Double>> coordinates;
        private String profile;
        private String profileName;
        private String format;
    }

    @Data
    public static class ORSEngine {
        private String version;
        private String build_date;
        private String graph_date;
        private String osm_date;
    }
}
