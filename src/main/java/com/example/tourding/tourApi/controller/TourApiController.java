package com.example.tourding.tourApi.controller;

import com.example.tourding.tourApi.dto.SearchKeyWordReqDto;
import com.example.tourding.tourApi.dto.SearchKeyWordRespDto;
import com.example.tourding.tourApi.service.TourApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tour")
public class TourApiController {

    private final TourApiService tourApiService;

    @GetMapping("/search-keyword")
    public List<SearchKeyWordRespDto> serachKeyword(@RequestBody SearchKeyWordReqDto searchKeyWordReqDto) {
        return tourApiService.searchByKeyword(searchKeyWordReqDto);
    }
}
