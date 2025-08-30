package com.example.tourding.external.kakao;

import lombok.Data;

import java.util.List;

@Data
public class KakaoSearchResponse {

    private List<Document> documents;
    private Meta meta;

    @Data
    public static class Document {
        private String address_name;
        private String category_group_code;
        private String category_group_name;
        private String category_name;
        private String distance;
        private String id;
        private String phone;
        private String place_name;
        private String place_url;
        private String road_address_name;
        private String x;
        private String y;
    }

    @Data
    public static class Meta {
        private boolean is_end;
        private int pageable_count;
        private SameName same_name;
        private int total_count;
    }

    @Data
    public static class SameName {
        private String keyword;
        private List<String> region;
        private String selected_region;
    }
}
