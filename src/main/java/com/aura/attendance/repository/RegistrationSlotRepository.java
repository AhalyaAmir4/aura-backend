package com.aura.attendance.repository;

import com.aura.attendance.entity.RegistrationSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RegistrationSlotRepository extends JpaRepository<RegistrationSlot, Long> {
    List<RegistrationSlot> findAllByOrderByStartTimeDesc();
}
