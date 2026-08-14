package com.example.tourding.ai.entity;

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
@Table(name = "user_riding_profile", schema = "tourding")
public class UserRidingProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 50)
    private String cyclingProfile;

    @Column(nullable = false, length = 30)
    private String skillLevel;

    @Column(nullable = false)
    private Boolean avoidHills;

    @Column(nullable = false)
    private Boolean preferPaved;

    @Column(nullable = false)
    private Boolean preferBikeRoad;

    @Column(nullable = false)
    private Boolean avoidMainRoad;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.cyclingProfile == null) this.cyclingProfile = "cycling-regular";
        if (this.skillLevel == null) this.skillLevel = "NORMAL";
        if (this.avoidHills == null) this.avoidHills = false;
        if (this.preferPaved == null) this.preferPaved = true;
        if (this.preferBikeRoad == null) this.preferBikeRoad = true;
        if (this.avoidMainRoad == null) this.avoidMainRoad = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
