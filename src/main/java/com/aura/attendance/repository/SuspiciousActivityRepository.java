package com.aura.attendance.repository;

import com.aura.attendance.entity.SuspiciousActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SuspiciousActivityRepository extends JpaRepository<SuspiciousActivity, Long> {
    List<SuspiciousActivity> findAllByOrderByCreatedAtDesc();
}
