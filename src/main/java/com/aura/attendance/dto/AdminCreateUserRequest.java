package com.aura.attendance.dto;
import com.aura.attendance.entity.User;
import lombok.*;
import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor
public class AdminCreateUserRequest {
    private String domain;
    private User.Department department;
    private Integer year;
    private String section;
    private List<StudentEntry> students;
    private List<TeacherEntry> teachers;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StudentEntry {
        private String fullName;
        private String rollNumber;
        private String parentEmail;
        private String phone;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TeacherEntry {
        private String fullName;
        private String facultyId;
        private String phone;
    }
}
