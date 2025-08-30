package com.example.tourding.kakao.controller;

import com.example.tourding.external.kakao.KakaoClient;
import com.example.tourding.kakao.dto.KakaoSearchReqDto;
import com.example.tourding.kakao.dto.KakaoSearchRespDto;
import com.example.tourding.kakao.service.KakaoApiService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/routes")

public class KakaoController {

    private final KakaoApiService kakaoApiService;

    @GetMapping("/convenience")
    @Operation(
            summary = "사용자의 현재 좌표를 입력받아서 근처 편의시설 정보를 리턴",
            description = "lon : 경도, lat: 위도 radius : 검색 반경 (m)단위 최대 20000 20km임" +
                    "type은 0 : 편의점, 1 : 화장실"
    )
    public List<KakaoSearchRespDto> kakaoSearch(@RequestBody KakaoSearchReqDto kakaoSearchReqDto) {
        return kakaoApiService.getKakaoSearchResults(kakaoSearchReqDto);
    }
}
