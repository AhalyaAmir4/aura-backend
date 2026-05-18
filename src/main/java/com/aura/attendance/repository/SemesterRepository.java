package com.aura.attendance.repository;

import com.aura.attendance.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findByIsActiveTrue();
    List<Semester> findAllByOrderByStartDateDesc();
}
