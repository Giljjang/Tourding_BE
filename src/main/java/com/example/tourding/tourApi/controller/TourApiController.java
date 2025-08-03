package com.example.tourding.tourApi.controller;

import com.example.tourding.tourApi.dto.SearchKeyWordRespDto;
import com.example.tourding.tourApi.service.TourApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tour")
public class TourApiController {

    private final TourApiService tourApiService;

    @GetMapping("/search-keyword")
    public List<SearchKeyWordRespDto> serachKeyword(@RequestParam String keyword, @RequestParam int pageNum) {
        return tourApiService.searchByKeyword(keyword,pageNum);
    }
}
