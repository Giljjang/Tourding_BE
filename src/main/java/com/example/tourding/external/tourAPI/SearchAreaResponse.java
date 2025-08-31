package com.example.tourding.external.tourAPI;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class SearchAreaResponse {
    private Response response;

    @Getter
    public static class Response {
        private Header header;
        private Body body;
    }

    @Getter
    public static class Header {
        private String resultMsg;
        private String resultCode;
    }

    @Getter
    public static class Body {
        private int numOfRows;
        private int pageNo;
        private int totalCount;
        private Items items;

        public List<Item> getItemList() {
            if(items == null || items.getItem() == null) {
                return Collections.emptyList();
            }
            return items.getItem();
        }
    }

    @Getter
    public static class Items {
        @JsonProperty("item")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) // item이 하나만 들어와도 리스트로 받을 수 있게
        private List<Item> item;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String title; // 장소 이름
        private String addr1; // 장소 주소
        private String cat1; // 관광지 타입 코드
        private String contentid; // 장소 고유 id
        private String contenttypeid; // 장소 고유 카테고리 id
        private String firstimage; // 장소 이미지1
        private String firstimage2; // 장소 이미지2
        private String mapx; // 장소 위도
        private String mapy; // 장소 경도
    }

}
