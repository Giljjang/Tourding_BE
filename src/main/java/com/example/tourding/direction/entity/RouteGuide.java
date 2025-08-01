package com.example.tourding.direction.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RouteGuide {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer sequenceNum; // 진행 순서 Open API Response에는 없는거임
    private Integer distance; // 이전 분기점의 경로 구성 좌표 인덱스로부터 해당 분기점의 경로 구성 좌표 인덱스까지의 거리(m)
    private Integer duration; // 이전 분기점의 경로 구성 좌표 인덱스로부터 해당 분기점의 경로 구성 좌표 인덱스까지의 소요 시간 (밀리초)
    @Column(columnDefinition = "TEXT")
    private String instructions; // 경로 안내 문구
    private Integer pointIndex; // 경로를 구성하는 좌표의 인덱스
    private Integer type; // 분기점 안내 타입
    // 타입 설명은 아래에
    // https://api.ncloud-docs.com/docs/ai-naver-mapsdirections-driving#%EB%B6%84%EA%B8%B0%EC%A0%90%EC%95%88%EB%82%B4%EC%BD%94%EB%93%9C

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summary_id", nullable = false)
    private RouteSummary summary;
}
