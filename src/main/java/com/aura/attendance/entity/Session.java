package com.aura.attendance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private User.Department department;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false, length = 1)
    private String section;

    @Column(nullable = false)
    private String qrSecret;

    private String shortCode;
    private LocalDateTime shortCodeExpiresAt;
    private Boolean shortCodeRevealed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @PrePersist
    protected void onCreate() { startedAt = LocalDateTime.now(); }

    public enum SessionStatus { ACTIVE, ENDED }
}
