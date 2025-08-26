package com.example.tourding.external.tourAPI;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DetailCommonResponse {
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
        private Items items;
        private int pageNo;
        private int totalCount;
    }

    @Getter
    public static class Items {
        private List<Item> item;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String overview;
        private String contentid;
        private String sigungucode;
        private String cat1;
        private String cat2;
        private String cat3;
        private String addr1;
        private String addr2;
        private String zipcode;
        private String mapx;
        private String mapy;
        private String mlevel;
        private String cpyrhtDivCd;
        private String contenttypeid;
        private String createdtime;
        private String homepage;
        private String modifiedtime;
        private String tel;
        private String telname;
        private String title;
        private String firstimage;
        private String firstimage2;
        private String areacode;
        private String lclsSystm1;
        private String lclsSystm2;
        private String lclsSystm3;
    }
}
