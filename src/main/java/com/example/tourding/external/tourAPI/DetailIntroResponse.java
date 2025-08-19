package com.example.tourding.external.tourAPI;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)

public class DetailIntroResponse {
    private Response response;

    @Getter
    public static class Response {
        private Header header;
        private Body body;
    }

    @Getter
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @Getter
    public static class Body {
        private int pageNo;
        private int numOfRows;
        private int totalCount;
        private Items items;
    }

    @Getter
    public static class Items {
        private List<Item> item;
    }

    @Getter
    public static class Item {
        private String chkpetculture;
        private String eventhomepage;
        private String eventplace;
        private String parkingleports;
        private String reservation;
        private String restdateleports;
        private String eventstartdate;
        private String festivalgrade;
        private String karaoke;
        private String discountinfofood;
        private String firstmenu;
        private String infocenterfood;
        private String kidsfacility;
        private String opendatefood;
        private String opentimefood;
        private String packing;
        private String parkingfood;
        private String reservationfood;
        private String chkcreditcardculture;
        private String scaleleports;
        private String usefeeleports;
        private String discountinfofestival;
        private String chkcreditcardfood;
        private String eventenddate;
        private String playtime;
        private String chkbabycarriageculture;
        private String roomcount;
        private String reservationlodging;
        private String reservationurl;
        private String roomtype;
        private String scalelodging;
        private String subfacility;
        private String barbecue;
        private String beauty;
        private String beverage;
        private String bicycle;
        private String campfire;
        private String fitness;
        private String placeinfo;
        private String parkinglodging;
        private String pickup;
        private String publicbath;
        private String opendate;
        private String parking;
        private String restdate;
        private String usetimeleports;
        private String foodplace;
        private String infocenterlodging;
        private String restdatefood;
        private String scalefood;
        private String seat;
        private String smoking;
        private String treatmenu;
        private String lcnsno;
        private String contentid;
        private String contenttypeid;
        private String accomcount;
        private String chkbabycarriage;
        private String chkcreditcard;
        private String chkpet;
        private String expagerange;
        private String expguide;
        private String heritage1;
        private String heritage2;
        private String heritage3;
        private String infocenter;
        private String taketime;
        private String theme;
        private String accomcountleports;
        private String chkbabycarriageleports;
        private String chkcreditcardleports;
        private String chkpetleports;
        private String expagerangeleports;
        private String infocenterleports;
        private String openperiod;
        private String parkingfeeleports;
        private String program;
        private String spendtimefestival;
        private String sponsor1;
        private String sponsor1tel;
        private String discountinfo;
        private String infocenterculture;
        private String parkingculture;
        private String parkingfee;
        private String restdateculture;
        private String usefee;
        private String usetimeculture;
        private String scale;
        private String spendtime;
        private String agelimit;
        private String bookingplace;
        private String useseason;
        private String usetime;
        private String accomcountculture;
        private String sponsor2;
        private String sponsor2tel;
        private String subevent;
        private String usetimefestival;
        private String distance;
        private String infocentertourcourse;
        private String schedule;
        private String publicpc;
        private String sauna;
        private String seminar;
        private String sports;
        private String refundregulation;
        private String chkbabycarriageshopping;
        private String chkcreditcardshopping;
        private String chkpetshopping;
        private String culturecenter;
        private String fairday;
        private String infocentershopping;
        private String opendateshopping;
        private String opentime;
        private String parkingshopping;
        private String restdateshopping;
        private String restroom;
        private String saleitem;
        private String saleitemcost;
        private String scaleshopping;
        private String shopguide;
        private String checkintime;
        private String checkouttime;
        private String chkcooking;
        private String accomcountlodging;
    }
}
