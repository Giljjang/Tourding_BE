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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String start;

    @Column(nullable = false)
    private String goal;

    @Column
    private String wayPoints; // null 가능

    @Column
    private String typeCode;

    @Column
    private String contentId;

    @Column(nullable = false)
    private String locateName; // 출발지,경유지,도착지 이름은 ","로 구분해서 들어옴

    @Column(nullable = false)
    private Boolean isUsed; // 실제 경로검색에 사용이 되었는지

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

}
