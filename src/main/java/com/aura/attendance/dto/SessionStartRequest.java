package com.aura.attendance.dto;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class SessionStartRequest {
    private Long subjectId;
    private String section;
    private String department; // optional override
    private Integer year;      // optional override
}
