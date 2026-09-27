package com.example.tourding.ai.entity;

import com.example.tourding.direction.entity.RouteSummary;
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
@Table(name = "ai_route_request", schema = "tourding")
public class AiRouteRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_summary_id", nullable = false)
    private RouteSummary routeSummary;

    @Column(nullable = false, length = 20)
    private String inputType;

    @Lob
    private String transcript;

    @Column(length = 50)
    private String intent;

    @Column(length = 50)
    private String action;

    @Column(nullable = false, length = 30)
    private String status;

    @Lob
    private String rejectionReason;

    private Double currentLon;
    private Double currentLat;
    private Integer sttLatencyMs;
    private Integer llmLatencyMs;
    private Integer orsLatencyMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
