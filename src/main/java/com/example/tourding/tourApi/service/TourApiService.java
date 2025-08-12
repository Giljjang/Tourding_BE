package com.example.tourding.tourApi.service;

import com.example.tourding.external.tourAPI.SearchAreaResponse;
import com.example.tourding.external.tourAPI.TourAPIClient;
import com.example.tourding.tourApi.dto.SearchCategoryReqDto;
import com.example.tourding.tourApi.dto.SearchKeyWordReqDto;
import com.example.tourding.tourApi.dto.SearchKeyAreaRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class TourApiService {
    private final TourAPIClient tourAPIClient;

    public List<SearchKeyAreaRespDto> searchByKeyword(SearchKeyWordReqDto searchKeyWordReqDto) {
        String keyword = searchKeyWordReqDto.getKeyword();
        int pageNum = searchKeyWordReqDto.getPageNum();
        String typeCode = searchKeyWordReqDto.getTypeCode();
        int areaCode = searchKeyWordReqDto.getAreaCode();

        SearchAreaResponse response = tourAPIClient.searchByKeyword(keyword, pageNum, typeCode, areaCode);

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
                .map(item -> SearchKeyAreaRespDto.builder()
                        .title(item.getTitle())
                        .addr1(item.getAddr1())
                        .contentid(item.getContentid())
                        .contenttypeid(item.getContenttypeid())
                        .firstimage(item.getFirstimage())
                        .firstimage2(item.getFirstimage2())
                        .mapx(item.getMapx())
                        .mapy(item.getMapy())
                        .build())
                .collect(Collectors.toList());
    }

    public List<SearchKeyAreaRespDto> searchByCategory(SearchCategoryReqDto searchCategoryReqDto) {
        int pageNum = searchCategoryReqDto.getPageNum();
        String typeCode = searchCategoryReqDto.getTypeCode();
        int areaCode = searchCategoryReqDto.getAreaCode();

        SearchAreaResponse response = tourAPIClient.searchByCategory(pageNum, typeCode, areaCode);

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
                .map(item -> SearchKeyAreaRespDto.builder()
                        .title(item.getTitle())
                        .addr1(item.getAddr1())
                        .contentid(item.getContentid())
                        .contenttypeid(item.getContenttypeid())
                        .firstimage(item.getFirstimage())
                        .firstimage2(item.getFirstimage2())
                        .mapx(item.getMapx())
                        .mapy(item.getMapy())
                        .build())
                .collect(Collectors.toList());
    }

}
