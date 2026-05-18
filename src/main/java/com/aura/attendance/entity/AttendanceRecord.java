package com.aura.attendance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id","student_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'PRESENT'")
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    // MANUAL_BY_TEACHER = teacher marked, OD_BY_ADMIN = admin granted OD
    private String deviceFingerprint;
    private String ipAddress;

    // OD reason (filled when status = OD)
    private String odReason;

    // markedAt is set on first insert; if updated (e.g. OD grant on existing ABSENT record)
    // we keep the original time unless it was never set.
    private LocalDateTime markedAt;

    @PrePersist
    protected void onCreate() {
        if (markedAt == null) markedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (markedAt == null) markedAt = LocalDateTime.now();
    }

    public enum AttendanceStatus { PRESENT, ABSENT, OD }
}
