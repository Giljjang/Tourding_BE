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

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNum ASC")
    @Builder.Default
    private List<RouteGuide> routeGuides = new ArrayList<>();

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNum ASC")
    @Builder.Default
    private List<RoutePath> routePaths = new ArrayList<>();

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNum ASC")
    @Builder.Default
    private List<RouteLocationName> routeLocationNames = new ArrayList<>();

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

}
