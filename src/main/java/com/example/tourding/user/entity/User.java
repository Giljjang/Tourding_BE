package com.example.tourding.user.entity;

import com.example.tourding.direction.entity.RouteSummary;
import com.example.tourding.user.dto.response.UserResponseDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor

@Table(name = "users", schema = "tourding")
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RouteSummary> summaries = new ArrayList<>();

    public User(String username, String password, String email) {
        this.username = username;
        this.passwordHash = password;
        this.email = email;
    }

    public void setUserInfo(String username, String password, String email) {
        this.username = username;
        this.passwordHash = password;
        this.email = email;
    }
    public void addSummary(RouteSummary routeSummary) {
        if (routeSummary == null) return;
        if (!summaries.contains(routeSummary)) {
            summaries.add(routeSummary);
            routeSummary.setUser(this);
        }
    }

    public void removeSummary(RouteSummary routeSummary) {
        if (routeSummary == null) return;
        if (summaries.remove(routeSummary)) {
            routeSummary.setUser(null);
        }
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
