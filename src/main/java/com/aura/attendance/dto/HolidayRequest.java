package com.aura.attendance.dto;
import com.aura.attendance.entity.Holiday;
import lombok.*;
import java.time.LocalDate;
@Data @NoArgsConstructor @AllArgsConstructor
public class HolidayRequest {
    private Long semesterId;
    private LocalDate holidayDate;
    private String description;
    private Holiday.HolidayType holidayType;
}
