package com.example.tourding.kakao.service;

import com.example.tourding.external.kakao.KakaoClient;
import com.example.tourding.external.kakao.KakaoSearchResponse;
import com.example.tourding.kakao.dto.KakaoSearchReqDto;
import com.example.tourding.kakao.dto.KakaoSearchRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class KakaoApiService {
    private final KakaoClient kakaoClient;

    public List<KakaoSearchRespDto> getKakaoSearchResults(KakaoSearchReqDto kakaoSearchReqDto) {
        String lat = kakaoSearchReqDto.getLat();
        String lon = kakaoSearchReqDto.getLon();
        String radius = kakaoSearchReqDto.getRadius();

        KakaoSearchResponse kakaoSearchResponse = kakaoClient.kakaoSearchConvenience(lon, lat, radius);

        List<KakaoSearchResponse.Document> documents = kakaoSearchResponse.getDocuments();
        if(documents == null) {
            return null;
        }

        return documents.stream()
                .map(document -> {
                    String type = "";
                    String categoryName = document.getCategory_name();
                    
                    if (categoryName != null) {
                        if (categoryName.contains("편의점")) {
                            type = "0";
                        } else if (categoryName.contains("화장실")) {
                            type = "1";
                        }
                    }
                    
                    return KakaoSearchRespDto.builder()
                            .name(document.getPlace_name())
                            .lat(document.getY())
                            .lon(document.getX())
                            .type(type)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
