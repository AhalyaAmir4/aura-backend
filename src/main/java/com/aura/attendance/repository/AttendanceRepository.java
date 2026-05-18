package com.aura.attendance.repository;

import com.aura.attendance.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByStudentId(Long studentId);
    List<AttendanceRecord> findBySessionId(Long sessionId);
    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);
    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    @Query("SELECT a FROM AttendanceRecord a WHERE a.student.id = :studentId ORDER BY a.markedAt DESC")
    List<AttendanceRecord> findByStudentIdOrderByMarkedAtDesc(@Param("studentId") Long studentId);
}
