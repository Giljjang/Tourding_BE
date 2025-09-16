package com.example.tourding.tourApi.controller;

import com.example.tourding.tourApi.dto.*;
import com.example.tourding.tourApi.service.TourApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/tour")
@Tag(name = "Tour API", description = "관광지 검색 관련 API")
public class TourApiController {

    private final TourApiService tourApiService;

    @PostMapping("/search-keyword")
    @Operation(
            summary = "키워드로 관광지 검색",
            description = "키워드, 페이지 번호, 관광지 타입, 지역 코드를 기반으로 관광지를 검색합니다."
    )
    public List<SearchAreaRespDto> searchKeyword(
            @Parameter(description = "검색 요청 정보", required = true)
            @RequestBody SearchKeyWordReqDto searchKeyWordReqDto
    ) {
        log.info("관광지 키워드 검색 호출: keyword={}, pageNo={}, 관광지 타입={}, 지역코드={}",
                searchKeyWordReqDto.getKeyword(),
                searchKeyWordReqDto.getPageNum(),
                searchKeyWordReqDto.getTypeCode(),
                searchKeyWordReqDto.getAreaCode());

        List<SearchAreaRespDto> result = tourApiService.searchByKeyword(searchKeyWordReqDto);

        log.info("관광지 키워드 검색 성공 - 결과 개수: {}", result.size());
        return result;
    }

    @PostMapping("/search-category")
    @Operation(summary = "지역, 관광타입으로 관광지 검색", description = "페이지 번호, 관광지 타입, 지역 코드를 기반으로 관광지를 검색합니다.")
    public List<SearchAreaRespDto> searchCategory(
            @Parameter(description = "검색 요청 정보", required = true)
            @RequestBody SearchCategoryReqDto searchCategoryReqDto
    ) {
        log.info("관광지 카테고리 검색 호출: pageNo={}, 관광지 타입={}, 지역코드={}",
                searchCategoryReqDto.getPageNum(),
                searchCategoryReqDto.getTypeCode(),
                searchCategoryReqDto.getAreaCode());

        List<SearchAreaRespDto> result = tourApiService.searchByCategory(searchCategoryReqDto);

        log.info("관광지 카테고리 검색 성공 - 결과 개수: {}", result.size());
        return result;
    }

    @PostMapping("/area-detail")
    @Operation(summary = "관광지 상세보기", description = "선택한 관광지의 contentId, contentTypeId를 기반으로 상세정보를 보여줍니다.")
    public DetailInfoRespDto areaDetail(
            @Parameter(description = "관광지의 contentId, contentTypeId", required = true)
            @RequestBody DetailInfoReqDto detailInfoReqDto
    ) {
        log.info("관광지 상세보기 호출: contentId={}, contentTypeId={}",
                detailInfoReqDto.getContentid(),
                detailInfoReqDto.getContenttypeid());

        DetailInfoRespDto result = tourApiService.searchDetailInfo(detailInfoReqDto);

        log.info("관광지 상세보기 성공 - title={}", result.getTitle());
        return result;
    }

    @PostMapping("/search-location")
    @Operation(
            summary = "사용자 위치기반 관광지 검색",
            description = "페이지 수, 경도, 위도, 검색 반경 (최대 20km)을 입력해서 사용자 위치 기반 장소를 검색합니다."
    )
    public List<SearchAreaRespDto> searchLocation(
            @Parameter(description = "위치 기반 검색 요청 정보", required = true)
            @RequestBody SearchLocationDto searchLocationDto
    ) {
        log.info("사용자 위치기반 관광지 검색 호출: pageNo={}, mapX={}, mapY={}, radius={}",
                searchLocationDto.getPageNum(),
                searchLocationDto.getMapX(),
                searchLocationDto.getMapY(),
                searchLocationDto.getRadius());

        List<SearchAreaRespDto> result = tourApiService.searchByLocation(searchLocationDto);

        log.info("사용자 위치기반 관광지 검색 성공 - 결과 개수: {}", result.size());
        return result;
    }
}
