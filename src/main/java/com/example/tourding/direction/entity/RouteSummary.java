package com.example.tourding.direction.entity;

import com.example.tourding.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "route_summary", schema = "tourding")
public class RouteSummary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private LocalDateTime departureTime; // 예상 도착 일시
    private Integer distance; // 전체 경로 거리
    private Integer duration; // 전체 경로 소요시간
    private Integer fuelPrice; // 주유 소모량
    private Integer taxiFare; // 택시요금
    private Integer tollFare; // 톨게이트 비용

    private String startLon; // 시작지점 경도
    private String startLat; // 시작지점 위도
    private String goalLon; // 도착지점 경도
    private String goalLat; // 도착지점 위도
    private Integer goalDir; // 경로상 진행방향을 중심으로 설정한 도착지의 위치를 나타낸 숫자 (0: 전방, 1:왼쪽, 2:오른쪽)

    // bbox : 전체 경로 경계 영역
    private String bboxSwLon; // 왼쪽 아래 경도
    private String bboxSwLat; // 왼쪽 아래 위도
    private String bboxNeLon; // 오른쪽 위 경도
    private String bboxNeLat; // 오른쪽 위 위도

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNum ASC")
    @Builder.Default
    private List<RouteSection> routeSections = new ArrayList<>();

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNum ASC")
    @Builder.Default
    private List<RouteGuide> routeGuides = new ArrayList<>();

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNum ASC")
    @Builder.Default
    private List<RoutePath> routePaths = new ArrayList<>();

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RouteLocationName> routeLocationNames = new ArrayList<>();

    public void addRouteSection(RouteSection routeSection) {
        routeSections.add(routeSection);
        routeSection.setSummary(this);
    }

    public void addRouteGuide(RouteGuide routeGuide) {
        routeGuides.add(routeGuide);
        routeGuide.setSummary(this);
    }

    public void addRoutePathPoint(RoutePath routePath) {
        routePaths.add(routePath);
        routePath.setSummary(this);
    }

    public void addRouteLocationName(RouteLocationName routeLocationName) {
        this.routeLocationNames.add(routeLocationName);
        routeLocationName.setSummary(this);
    }

    @PrePersist // 엔티티가 최조 저장되기 직전에 자동으로 호출되어서 createdAt을 자동으로 설정해줌
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void update(RouteSummary routeSummary) {
        this.departureTime = routeSummary.departureTime;
        this.distance = routeSummary.distance;
        this.duration = routeSummary.duration;
        this.fuelPrice = routeSummary.fuelPrice;
        this.taxiFare = routeSummary.taxiFare;
        this.tollFare = routeSummary.tollFare;
        this.startLon = routeSummary.startLon;
        this.startLat = routeSummary.startLat;
        this.goalLon = routeSummary.goalLon;
        this.goalLat = routeSummary.goalLat;
        this.goalDir = routeSummary.goalDir;
        this.bboxSwLon = routeSummary.bboxSwLon;
        this.bboxSwLat = routeSummary.bboxSwLat;
        this.bboxNeLon = routeSummary.bboxNeLon;
        this.bboxNeLat = routeSummary.bboxNeLat;
    }
}
