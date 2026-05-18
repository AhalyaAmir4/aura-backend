package com.aura.attendance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppRole role;

    @Enumerated(EnumType.STRING)
    private Department department;

    private Integer year;

    private String section;

    @Column(unique = true)
    private String rollNumber;

    @Column(unique = true)
    private String facultyId;

    private String parentEmail;
    private String phone;
    private String deviceFingerprint;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ---- Enums ----
    public enum AppRole { admin, teacher, student }

    public enum Department { ECE, CSE, IT, AIDS, MECH, CIVIL, EEE }
}
