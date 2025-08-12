package com.example.tourding.tourApi.controller;

import com.example.tourding.tourApi.dto.SearchCategoryReqDto;
import com.example.tourding.tourApi.dto.SearchKeyWordReqDto;
import com.example.tourding.tourApi.dto.SearchKeyAreaRespDto;
import com.example.tourding.tourApi.service.TourApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "검색 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(
                    type = "array",
                    implementation = SearchKeyAreaRespDto.class
                ),
                examples = @ExampleObject(
                    name = "성공 응답 예시",
                    value = """
                    [
                        {
                            "title": "대구iM뱅크PARK",
                            "addr1": "대구광역시 북구 고성로 191",
                            "contentid": "2606238",
                            "contenttypeid": "28",
                            "firstimage": "http://tong.visitkorea.or.kr/cms/resource/72/3501372_image2_1.jpg",
                            "firstimage2": "http://tong.visitkorea.or.kr/cms/resource/72/3501372_image3_1.jpg",
                            "mapx": "128.5861797933",
                            "mapy": "35.8830627794"
                        }
                    ]
                    """
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public List<SearchKeyAreaRespDto> searchKeyword(
        @Parameter(
            description = "검색 요청 정보",
            required = true
        )
        @RequestBody SearchKeyWordReqDto searchKeyWordReqDto
    ) {
        return tourApiService.searchByKeyword(searchKeyWordReqDto);
    }

    @PostMapping("/search-category")
    @Operation(
            summary = "지역, 관광타입으로 관광지 검색",
            description = "페이지 번호, 관광지 타입, 지역 코드를 기반으로 관광지를 검색합니다."
    )
    public List<SearchKeyAreaRespDto> searchCategory(
            @Parameter(
                    description = "검색 요청 정보",
                    required = true
            )
            @RequestBody SearchCategoryReqDto searchCategoryReqDto
    ) {
        return tourApiService.searchByCategory(searchCategoryReqDto);
    }
}
