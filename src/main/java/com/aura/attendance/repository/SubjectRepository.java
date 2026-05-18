package com.aura.attendance.repository;

import com.aura.attendance.entity.Subject;
import com.aura.attendance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByDepartmentAndYear(User.Department dept, Integer year);
    List<Subject> findAllByOrderByDepartmentAscYearAsc();
}
