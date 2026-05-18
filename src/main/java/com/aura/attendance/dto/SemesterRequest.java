package com.aura.attendance.dto;
import com.aura.attendance.entity.Semester;
import lombok.*;
import java.time.LocalDate;
@Data @NoArgsConstructor @AllArgsConstructor
public class SemesterRequest {
    private String name;
    private String academicYear;
    private Semester.SemesterType semesterType;
    private LocalDate startDate;
    private LocalDate endDate;
}
