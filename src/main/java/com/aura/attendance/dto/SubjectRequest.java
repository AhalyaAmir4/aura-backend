package com.aura.attendance.dto;
import com.aura.attendance.entity.User;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class SubjectRequest {
    private String name;
    private String code;
    private Integer credits;
    private User.Department department;
    private Integer year;
}
