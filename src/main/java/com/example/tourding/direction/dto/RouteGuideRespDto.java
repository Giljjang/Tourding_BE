package com.example.tourding.direction.dto;

import com.example.tourding.direction.entity.RouteGuide;
import com.example.tourding.external.naver.ApiRouteResponse;
import jakarta.persistence.Column;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RouteGuideRespDto {
    private Integer sequenceNum; // 진행 순서 Open API Response에는 없는거임
    private Integer distance; // 이전 분기점의 경로 구성 좌표 인덱스로부터 해당 분기점의 경로 구성 좌표 인덱스까지의 거리(m)
    private Integer duration; // 이전 분기점의 경로 구성 좌표 인덱스로부터 해당 분기점의 경로 구성 좌표 인덱스까지의 소요 시간 (밀리초)
    @Column(columnDefinition = "TEXT")
    private String instructions; // 경로 안내 문구
    private String locationName; // 장소 이름
    private Integer pointIndex; // 경로를 구성하는 좌표의 인덱스
    private Integer type; // 분기점 안내 타입
    private String lon; // 경도
    private String lat; // 위도

    public static RouteGuideRespDto from(ApiRouteResponse.Guide guide, int sequenceNum, List<String> locationNames) {
        RouteGuideRespDto.RouteGuideRespDtoBuilder builder = RouteGuideRespDto.builder()
                .distance(guide.getDistance())
                .duration(guide.getDuration())
                .instructions(guide.getInstructions())
                .pointIndex(guide.getPointIndex())
                .type(guide.getType())
                .lon(guide.getLon())
                .lat(guide.getLat())
                .sequenceNum(sequenceNum);

        if ("출발지".equals(guide.getInstructions())) {
            builder.locationName(locationNames.get(0));
        } else if ("도착지".equals(guide.getInstructions())) {
            builder.locationName(locationNames.get(locationNames.size() - 1));
        }

        return builder.build();
    }

    public static RouteGuideRespDto fromEntity(RouteGuide guide, int sequenceNum) {
        return RouteGuideRespDto.builder()
                .distance(guide.getDistance())
                .duration(guide.getDuration())
                .instructions(guide.getInstructions())
                .pointIndex(guide.getPointIndex())
                .lon(guide.getLon())
                .lat(guide.getLat())
                .sequenceNum(sequenceNum)
                .locationName(guide.getLocationName())
                .type(guide.getType())
                .build();
    }
}
