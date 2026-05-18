package com.aura.attendance.dto;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class MarkAttendanceRequest {
    private String qrToken;
    private String shortCode;
    private String fingerprint;
}
