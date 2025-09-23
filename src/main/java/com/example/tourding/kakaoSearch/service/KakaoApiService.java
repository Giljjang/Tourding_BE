package com.example.tourding.kakaoSearch.service;

import com.example.tourding.external.kakao.KakaoClient;
import com.example.tourding.external.kakao.KakaoSearchResponse;
import com.example.tourding.kakaoSearch.dto.KakaoSearchReqDto;
import com.example.tourding.kakaoSearch.dto.KakaoSearchRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class KakaoApiService {
    private final KakaoClient kakaoClient;

    public List<KakaoSearchRespDto> getKakaoSearch(KakaoSearchReqDto kakaoSearchReqDto, String query) {
        String lat = kakaoSearchReqDto.getLat();
        String lon = kakaoSearchReqDto.getLon();
        String radius = kakaoSearchReqDto.getRadius();

        KakaoSearchResponse kakaoSearchResponse = kakaoClient.kakaoSearchByLocation(lon, lat, radius, query);

        List<KakaoSearchResponse.Document> documents = kakaoSearchResponse.getDocuments();
        if(documents == null) {
            return null;
        }

        return documents.stream()
                .map(document -> KakaoSearchRespDto.builder()
                            .name(document.getPlace_name())
                            .lat(document.getY())
                            .lon(document.getX())
                            .build())
                .collect(Collectors.toList());
    }
}
