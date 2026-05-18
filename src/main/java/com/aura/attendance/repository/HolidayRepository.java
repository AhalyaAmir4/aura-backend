package com.aura.attendance.repository;

import com.aura.attendance.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findBySemesterIdOrderByHolidayDate(Long semesterId);
}
