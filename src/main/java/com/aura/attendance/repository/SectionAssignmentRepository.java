package com.aura.attendance.repository;

import com.aura.attendance.entity.SectionAssignment;
import com.aura.attendance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SectionAssignmentRepository extends JpaRepository<SectionAssignment, Long> {
    List<SectionAssignment> findByTeacherId(Long teacherId);
    List<SectionAssignment> findByDepartmentAndYear(User.Department dept, Integer year);
    Optional<SectionAssignment> findByDepartmentAndYearAndSectionAndSubjectId(User.Department dept, Integer year, String section, Long subjectId);
}
