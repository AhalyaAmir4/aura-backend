package com.aura.attendance.dto;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String token;
    private Long id;
    private String email;
    private String fullName;
    private String role;
    private String department;
    private Integer year;
    private String section;
    private String rollNumber;
    private String facultyId;
    private String parentEmail;
    private String phone;
    private String deviceFingerprint; // current enrolled device (null if none)
}
