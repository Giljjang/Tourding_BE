package com.example.tourding.direction.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "route_location", schema = "tourding")
public class RouteLocationName {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer sequenceNum;
    private String lon; // 경도
    private String lat; // 위도
    private String name;
    private String type;
    private String typeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summary_id", nullable = false)
    private RouteSummary summary;
}
