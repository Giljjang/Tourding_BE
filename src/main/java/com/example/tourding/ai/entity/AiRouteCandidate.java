package com.example.tourding.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "ai_route_candidate", schema = "tourding")
public class AiRouteCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_route_request_id", nullable = false)
    private AiRouteRequest aiRouteRequest;

    private Integer rankNo;
    private Double score;
    private Double distance;
    private Double duration;
    private Double ascent;
    private Double descent;
    private Double comfortScore;
    private Double flatnessScore;
    private Double surfaceScore;
    private Double waytypeScore;
    private Double efficiencyScore;

    @Lob
    private String weightJson;

    @Lob
    private String extraSummaryJson;

    @Lob
    private String geometryJson;

    @Column(nullable = false)
    private Boolean selected;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.selected == null) this.selected = false;
    }
}
