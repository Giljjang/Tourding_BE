package com.example.tourding.tourApi.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
@Builder

public class DetailInfoRespDto {
    // ===== 공통 필드 =====
    private String contentid;
    private String typeCode;
    private String contenttypeid;
    private String homepage;
    private String tel;
    private String telname;
    private String title;
    private String firstimage;
    private String firstimage2;
    private String address;
    private String overview;

    // ===== 12 (관광지) =====
    private String parking;    // 주차시설
    private String useseason;  // 이용시기
    private OpenInfo openInfo; // 이용시간, 쉬는날

    @Data
    @Builder
    public static class OpenInfo {
        private String usetime;    // 이용시간
        private String restdate;   // 쉬는날
    }

    // ===== 14 (문화시설) =====
    private String infocenterculture;  // 문의및안내
    private String restdateculture;    // 쉬는날
    private String usefee;             // 이용요금
    private String usetimeculture;     // 이용시간
    private String scale;              // 규모
    private String spendtime;          // 관람소요시간
    private ParkingInfo parkingInfo; // 주차정보

    @Data
    @Builder
    public static class ParkingInfo {
        private String parkingculture;     // 주차시설
        private String parkingfee;         // 주차요금
    }

    // ===== 15 (행사/공연/축제) =====
    private String bookingplace;          // 예매처
    private String discountinfofestival;  // 할인정보
    private String eventplace;            // 행사장소
    private String playtime;              // 공연시간
    private String program;               // 행사프로그램
    private String spendtimefestival;     // 관람소요시간
    private String usetimefestival;       // 이용요금
    private FestivalDurationInfo festivalDurationInfo; // 행사 시작,종료일 정보

    @Data
    @Builder
    public static class FestivalDurationInfo {
        private String eventstartdate;        // 행사시작일
        private String eventenddate;          // 행사종료일
    }

    // ===== 25 (여행코스) =====
    private String distance;             // 코스총거리
    private String infocentertourcourse; // 문의및안내
    private String schedule;             // 코스일정
    private String taketime;             // 총소요시간
    private String theme;                // 코스테마

    // ===== 28 (레포츠) =====
    private String reservation;       // 예약안내
    private String scaleleports;      // 규모
    private String usefeeleports;     // 입장료
    private LeportsOpenInfo leportsOpenInfo; // 레포츠 영업 정보
    private LeportsParkingInfo leportsParkingInfo; // 레포츠 주차시설 정보

    @Data
    @Builder
    public static class LeportsOpenInfo {
        private String openperiod;        // 개장기간
        private String restdateleports;   // 쉬는날
        private String usetimeleports;    // 이용시간
    }

    @Data
    @Builder
    public static class LeportsParkingInfo {
        private String parkingfeeleports; // 주차요금
        private String parkingleports;    // 주차시설
    }

    // ===== 32 (숙박) =====
    private String parkinglodging;    // 주차시설
    private String reservationurl;    // 예약안내홈페이지
    private String barbecue;          // 바비큐 여부
    private String bicycle;           // 자전거 대여 여부
    private String campfire;          // 캠프파이어 여부
    private String refundregulation;  // 환불규정
    private CheckInOutInfo checkInOutInfo; // 체크인, 아웃 정보

    @Data
    @Builder
    public static class CheckInOutInfo {
        private String checkintime;       // 입실시간`
        private String checkouttime;      // 퇴실시간`
    }

    // ===== 38 (쇼핑) =====
    private String infocentershopping; // 문의및안내
    private String parkingshopping;    // 주차시설
    private String restroom;           // 화장실
    private String shopguide;          // 매장안내
    private StoreOpenInfo storeOpenInfo; // 매장 영업시간 정보

    @Data
    @Builder
    public static class StoreOpenInfo {
        private String opendateshopping;   // 개장일
        private String opentime;           // 영업시간
        private String restdateshopping;   // 쉬는날
    }

    // ===== 39 (음식점) =====
    private String packing;         // 포장가능
    private String parkingfood;     // 주차시설
    private String treatmenu;       // 취급메뉴
    private FoodOpenInfo foodOpenInfo;

    @Data
    @Builder
    public static class FoodOpenInfo {
        private String opendatefood;    // 개업일
        private String opentimefood;    // 영업시간
    }
}
