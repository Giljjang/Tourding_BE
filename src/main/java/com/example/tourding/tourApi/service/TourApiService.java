package com.example.tourding.tourApi.service;

import com.example.tourding.external.tourAPI.DetailCommonResponse;
import com.example.tourding.external.tourAPI.DetailIntroResponse;
import com.example.tourding.external.tourAPI.SearchAreaResponse;
import com.example.tourding.external.tourAPI.TourAPIClient;
import com.example.tourding.tourApi.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class TourApiService {
    private final TourAPIClient tourAPIClient;

    public List<SearchAreaRespDto> searchByKeyword(SearchKeyWordReqDto searchKeyWordReqDto) {
        String keyword = searchKeyWordReqDto.getKeyword();
        int pageNum = searchKeyWordReqDto.getPageNum();
        String typeCode = searchKeyWordReqDto.getTypeCode();
        int areaCode = searchKeyWordReqDto.getAreaCode();

        SearchAreaResponse response = tourAPIClient.searchByKeyword(keyword, pageNum, typeCode, areaCode);

        return praseDto(response);
    }

    public List<SearchAreaRespDto> searchByCategory(SearchCategoryReqDto searchCategoryReqDto) {
        int pageNum = searchCategoryReqDto.getPageNum();
        String typeCode = searchCategoryReqDto.getTypeCode();
        int areaCode = searchCategoryReqDto.getAreaCode();

        SearchAreaResponse response = tourAPIClient.searchByCategory(pageNum, typeCode, areaCode);

        return praseDto(response);
    }

    public List<SearchAreaRespDto> searchByLocation(SearchLocationDto searchLocationReqDto) {
        int pageNum = searchLocationReqDto.getPageNum();
        String mapX = searchLocationReqDto.getMapX();
        String mapY = searchLocationReqDto.getMapY();
        String radius = searchLocationReqDto.getRadius();
        String typeCode = searchLocationReqDto.getTypeCode();

        SearchAreaResponse response = tourAPIClient.searchLocationDto(pageNum, mapX, mapY, radius, typeCode);
        return praseDto(response);
    }

    private List<SearchAreaRespDto> praseDto(SearchAreaResponse response) {
        if(response != null) {
            List<SearchAreaResponse.Item> items = response.getResponse()
                    .getBody()
                    .getItemList();

            if(response.getResponse() == null
                    || response.getResponse().getBody() == null
                    || response.getResponse().getBody().getItems() == null
                    || response.getResponse().getBody().getItems().getItem() == null) {
                return Collections.emptyList();
            }

            return items.stream()
                    .map(item -> SearchAreaRespDto.builder()
                            .title(item.getTitle())
                            .addr1(item.getAddr1())
                            .typeCode(item.getCat1())
                            .contentid(item.getContentid())
                            .contenttypeid(item.getContenttypeid())
                            .firstimage(item.getFirstimage())
                            .firstimage2(item.getFirstimage())
                            .mapx(item.getMapx())
                            .mapy(item.getMapy())
                            .build())
                    .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    public DetailInfoRespDto searchDetailInfo(DetailInfoReqDto detailInfoReqDto) {
        String contentId = detailInfoReqDto.getContentid();
        String contentTypeId = detailInfoReqDto.getContenttypeid();

        DetailCommonResponse detailCommonResponse = tourAPIClient.searchDetailCommon(contentId);
        DetailIntroResponse detailIntroResponse = tourAPIClient.searchDetailIntro(contentId, contentTypeId);

        List<DetailCommonResponse.Item> common = detailCommonResponse.getResponse().getBody().getItems().getItem();
        DetailCommonResponse.Item commonItem = common.isEmpty() ? null : common.get(0);

        List<DetailIntroResponse.Item> intro = detailIntroResponse.getResponse().getBody().getItems().getItem();
        DetailIntroResponse.Item introItem = common.isEmpty() ? null : intro.get(0);

        String address = commonItem.getAddr1() + commonItem.getAddr2() + " (" + commonItem.getZipcode() + ")";

        return DetailInfoRespDto.builder()
                // ===== 공통 필드 =====
                .contentid(commonItem.getContentid())
                .typeCode(commonItem.getCat1())
                .contenttypeid(commonItem.getContenttypeid())
                .homepage(commonItem.getHomepage())
                .tel(commonItem.getTel())
                .telname(commonItem.getTelname())
                .firstimage(commonItem.getFirstimage())
                .firstimage2(commonItem.getFirstimage2())
                .address(address)
                .overview(commonItem.getOverview())
                // ===== 12 (관광지) =====
                .packing(introItem.getPacking())
                .useseason(introItem.getUseseason())
                .openInfo(
                        DetailInfoRespDto.OpenInfo.builder()
                                .usetime(introItem.getUsetime())
                                .restdate(introItem.getRestdate())
                                .build()
                )

                // ===== 14 (문화시설) =====
                .infocenterculture(introItem.getInfocenterculture())
                .restdateculture(introItem.getRestdateculture())
                .usefee(introItem.getUsefee())
                .usetimeculture(introItem.getUsetimeculture())
                .scale(introItem.getScale())
                .spendtime(introItem.getSpendtime())
                .parkingInfo(
                        DetailInfoRespDto.ParkingInfo.builder()
                                .parkingculture(introItem.getParkingculture())
                                .parkingfee(introItem.getParkingfee())
                                .build()
                )

                // ===== 15 (행사/공연/축제) =====
                .bookingplace(introItem.getBookingplace())
                .discountinfofestival(introItem.getDiscountinfofestival())
                .eventplace(introItem.getEventplace())
                .playtime(introItem.getPlaytime())
                .program(introItem.getProgram())
                .usetimefestival(introItem.getUsetimefestival())
                .festivalDurationInfo(
                        DetailInfoRespDto.FestivalDurationInfo.builder()
                                .eventstartdate(introItem.getEventstartdate())
                                .eventenddate(introItem.getEventenddate())
                                .build()
                )

                // ===== 25 (여행코스) =====
                .distance(introItem.getDistance())
                .infocentertourcourse(introItem.getInfocentertourcourse())
                .schedule(introItem.getSchedule())
                .taketime(introItem.getTaketime())
                .theme(introItem.getTheme())

                // ===== 28 (레포츠) =====
                .reservation(introItem.getReservation())
                .scaleleports(introItem.getScaleleports())
                .usefeeleports(introItem.getUsefeeleports())
                .leportsOpenInfo(
                        DetailInfoRespDto.LeportsOpenInfo.builder()
                                .openperiod(introItem.getOpenperiod())
                                .restdateleports(introItem.getRestdateleports())
                                .usetimeleports(introItem.getUsetimeleports())
                                .build()
                )
                .leportsParkingInfo(
                        DetailInfoRespDto.LeportsParkingInfo.builder()
                                .parkingfeeleports(introItem.getParkingfeeleports())
                                .parkingleports(introItem.getParkingleports())
                                .build()
                )

                // ===== 32 (숙박) =====
                .parkinglodging(introItem.getParkinglodging())
                .reservationurl(introItem.getReservationurl())
                .barbecue(introItem.getBarbecue())
                .bicycle(introItem.getBicycle())
                .campfire(introItem.getCampfire())
                .refundregulation(introItem.getRefundregulation())
                .checkInOutInfo(
                        DetailInfoRespDto.CheckInOutInfo.builder()
                                .checkintime(introItem.getCheckintime())
                                .checkouttime(introItem.getCheckouttime())
                                .build()
                )

                // ===== 38 (쇼핑) =====
                .infocentershopping(introItem.getInfocentershopping())
                .parkingshopping(introItem.getParkingshopping())
                .restroom(introItem.getRestroom())
                .shopguide(introItem.getShopguide())
                .storeOpenInfo(
                        DetailInfoRespDto.StoreOpenInfo.builder()
                                .opendateshopping(introItem.getOpendateshopping())
                                .opentime(introItem.getOpentime())
                                .restdateshopping(introItem.getRestdateshopping())
                                .build()
                )

                // ===== 39 (음식점) =====
                .packing(introItem.getPacking())
                .parkingfood(introItem.getParkingfood())
                .treatmenu(introItem.getTreatmenu())
                .foodOpenInfo(
                        DetailInfoRespDto.FoodOpenInfo.builder()
                                .opentimefood(introItem.getOpentimefood())
                                .opendatefood(introItem.getOpendatefood())
                                .build()
                )
                .build();
    }

}
