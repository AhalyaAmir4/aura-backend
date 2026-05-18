package com.aura.attendance.dto;
import com.aura.attendance.entity.User;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class SectionAssignRequest {
    private User.Department department;
    private Integer year;
    private String section;
    private Long subjectId;
    private Long teacherId;
}
