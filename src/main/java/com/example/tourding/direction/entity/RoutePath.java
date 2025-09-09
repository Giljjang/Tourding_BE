package com.example.tourding.direction.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "route_path", schema = "tourding")
public class RoutePath { // 경로를 구성하는 모든 좌표 표시
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer sequenceNum; // path 진행 순서 Open API Response에는 없는거임
    private String lon; // 경도
    private String lat; // 위도

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summary_id", nullable = false)
    private RouteSummary summary;
}
