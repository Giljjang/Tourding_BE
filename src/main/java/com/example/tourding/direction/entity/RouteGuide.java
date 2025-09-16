package com.example.tourding.direction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "route_guide", schema = "tourding")
public class RouteGuide {
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private Integer sequenceNum; // 진행 순서 Open API Response에는 없는거임
    private Integer distance; // 이전 분기점의 경로 구성 좌표 인덱스로부터 해당 분기점의 경로 구성 좌표 인덱스까지의 거리(m)
    private Integer duration; // 이전 분기점의 경로 구성 좌표 인덱스로부터 해당 분기점의 경로 구성 좌표 인덱스까지의 소요 시간 (밀리초)
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String instructions; // 경로 안내 문구
    private String locationName; // 장소 이름
    private Integer pointIndex; // 경로를 구성하는 좌표의 인덱스
    private Integer type; // 분기점 안내 타입
    private String lon; // 경도
    private String lat; // 위도
    // 타입 설명은 아래에
    // https://api.ncloud-docs.com/docs/ai-naver-mapsdirections-driving#%EB%B6%84%EA%B8%B0%EC%A0%90%EC%95%88%EB%82%B4%EC%BD%94%EB%93%9C

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summary_id", nullable = false)
    private RouteSummary summary;
}
