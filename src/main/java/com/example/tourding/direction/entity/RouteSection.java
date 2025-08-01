package com.example.tourding.direction.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RouteSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer sequenceNum; // 순서 보존용
    private String name; // 도로 이름
    private Integer congestion; // 혼잡도 분류 코드 (0: 값없음, 1: 원활, 2: 서행, 3:혼잡)
    private Integer distance; // (거리 m)
    private Integer speed; // 평균 속도
    private Integer pointCount; // 형상점 수
    private Integer pointIndex; // 경로를 구성하는 좌표의 인덱스

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summary_id", nullable = false)
    private RouteSummary summary;
}
