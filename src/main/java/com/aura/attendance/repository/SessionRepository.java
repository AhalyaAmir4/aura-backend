package com.aura.attendance.repository;

import com.aura.attendance.entity.Session;
import com.aura.attendance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByTeacherIdAndStatus(Long teacherId, Session.SessionStatus status);
    Optional<Session> findByDepartmentAndYearAndSectionAndStatus(
            User.Department dept, Integer year, String section, Session.SessionStatus status);
    List<Session> findByTeacherIdOrderByStartedAtDesc(Long teacherId);
    List<Session> findAllByOrderByStartedAtDesc();
}
