package com.aura.attendance.dto;
import com.aura.attendance.entity.User;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    private String fullName;
    private String email;
    private String password;
    private User.AppRole role;
    private User.Department department;
    private Integer year;
    private String section;
    private String rollNumber;
    private String facultyId;
    private String parentEmail;
    private String phone;
    private String adminSecret;
}
