package com.example.tourding.kakaoSearch.controller;

import com.example.tourding.kakaoSearch.dto.KakaoSearchReqDto;
import com.example.tourding.kakaoSearch.dto.KakaoSearchRespDto;
import com.example.tourding.kakaoSearch.service.KakaoApiService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/routes")

public class KakaoController {

    private final KakaoApiService kakaoApiService;

    @PostMapping("/convenience-store")
    @Operation(
            summary = "사용자의 현재 좌표를 입력받아서 근처 편의점 정보를 리턴",
            description = "lon : 경도, lat: 위도 radius : 검색 반경 (m)단위 최대 20000 20km임"
    )
    public List<KakaoSearchRespDto> kakaoSearchConvenienceStore(@RequestBody KakaoSearchReqDto kakaoSearchReqDto) {
        return kakaoApiService.getKakaoSearch(kakaoSearchReqDto,"편의점");
    }

    @PostMapping("/toilet")
    @Operation(
            summary = "사용자의 현재 좌표를 입력받아서 근처 화장실 정보를 리턴",
            description = "lon : 경도, lat: 위도 radius : 검색 반경 (m)단위 최대 20000 20km임"
    )
    public List<KakaoSearchRespDto> kakaoSearchToilet(@RequestBody KakaoSearchReqDto kakaoSearchReqDto) {
        return kakaoApiService.getKakaoSearch(kakaoSearchReqDto,"화장실");
    }
}
