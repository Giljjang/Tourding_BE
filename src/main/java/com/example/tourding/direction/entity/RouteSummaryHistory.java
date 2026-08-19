package com.example.tourding.direction.entity;

import com.example.tourding.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "route_summary_history", schema = "tourding")
public class RouteSummaryHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_summary_id", nullable = false)
    private RouteSummary routeSummary;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false)
    private String start;

    @Column(nullable = false)
    private String goal;

    @Column
    private String wayPoints;

    @Column
    private String typeCode;

    @Column
    private String contentId;

    @Column
    private String contentTypeId;

    @Column(nullable = false)
    private String locateName;

    @Column(nullable = false)
    private Boolean isUsed;

    @Column(length = 50)
    private String cyclingProfile;

    @Column
    private Boolean fastRoute;

    @Column
    private Boolean avoidSteps;

    @Column
    private Boolean avoidFords;

    @Column(length = 30)
    private String skillLevel;

    @Column
    private Double preferenceScore;

    @Lob
    private String extraInfoJson;

    @Lob
    private String routeGeometryJson;

    @Column(nullable = false)
    private Boolean restored;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime restoredAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.restored == null) {
            this.restored = false;
        }
    }
}
