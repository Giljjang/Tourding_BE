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

        SearchAreaResponse response = tourAPIClient.searchLocationDto(pageNum, mapX, mapY, radius);
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
                .contenttypeid(commonItem.getContenttypeid())
                .homepage(commonItem.getHomepage())
                .tel(commonItem.getTel())
                .telname(commonItem.getTelname())
                .firstimage(commonItem.getFirstimage())
                .firstimage2(commonItem.getFirstimage2())
                .address(address)
                .overview(commonItem.getOverview())
                // ===== 12 (관광지) =====
                .opendate(introItem.getOpendate())
                .packing(introItem.getPacking())
                .restdate(introItem.getRestdate())
                .useseason(introItem.getUseseason())
                .usetime(introItem.getUsetime())
                // ===== 14 (문화시설) =====
                .infocenterculture(introItem.getInfocenterculture())
                .parkingculture(introItem.getParkingculture())
                .parkingfee(introItem.getParkingfee())
                .restdateculture(introItem.getRestdateculture())
                .usefee(introItem.getUsefee())
                .usetimeculture(introItem.getUsetimeculture())
                .scale(introItem.getScale())
                .spendtime(introItem.getSpendtime())
                // ===== 15 (행사/공연/축제) =====
                .bookingplace(introItem.getBookingplace())
                .discountinfofestival(introItem.getDiscountinfofestival())
                .eventenddate(introItem.getEventenddate())
                .eventplace(introItem.getEventplace())
                .eventstartdate(introItem.getEventstartdate())
                .playtime(introItem.getPlaytime())
                .program(introItem.getProgram())
                .usetimefestival(introItem.getUsetimefestival())

                // ===== 25 (여행코스) =====
                .distance(introItem.getDistance())
                .infocentertourcourse(introItem.getInfocentertourcourse())
                .schedule(introItem.getSchedule())
                .taketime(introItem.getTaketime())
                .theme(introItem.getTheme())

                // ===== 28 (레포츠) =====
                .openperiod(introItem.getOpenperiod())
                .parkingfeeleports(introItem.getParkingfeeleports())
                .parkingleports(introItem.getParkingleports())
                .reservation(introItem.getReservation())
                .restdateleports(introItem.getRestdateleports())
                .scaleleports(introItem.getScaleleports())
                .usefeeleports(introItem.getUsefeeleports())
                .usetimeleports(introItem.getUsetimeleports())

                // ===== 32 (숙박) =====
                .checkintime(introItem.getCheckintime())
                .checkouttime(introItem.getCheckouttime())
                .parkinglodging(introItem.getParkinglodging())
                .reservationurl(introItem.getReservationurl())
                .barbecue(introItem.getBarbecue())
                .bicycle(introItem.getBicycle())
                .campfire(introItem.getCampfire())
                .refundregulation(introItem.getRefundregulation())

                // ===== 38 (쇼핑) =====
                .infocentershopping(introItem.getInfocentershopping())
                .opendateshopping(introItem.getOpendateshopping())
                .opentime(introItem.getOpentime())
                .parkingshopping(introItem.getParkingshopping())
                .restdateshopping(introItem.getRestdateshopping())
                .restroom(introItem.getRestroom())
                .shopguide(introItem.getShopguide())

                // ===== 39 (음식점) =====
                .opendatefood(introItem.getOpendatefood())
                .opentimefood(introItem.getOpentimefood())
                .packing(introItem.getPacking())
                .parkingfood(introItem.getParkingfood())
                .treatmenu(introItem.getTreatmenu())

                .build();
    }

}
