package com.aura.attendance.repository;

import com.aura.attendance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRole(User.AppRole role);
    List<User> findByDepartmentAndYearAndSection(User.Department dept, Integer year, String section);
    long countByRole(User.AppRole role);
}
