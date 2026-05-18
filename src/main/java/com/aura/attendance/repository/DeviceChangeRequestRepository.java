package com.aura.attendance.repository;

import com.aura.attendance.entity.DeviceChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceChangeRequestRepository extends JpaRepository<DeviceChangeRequest, Long> {
    List<DeviceChangeRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<DeviceChangeRequest> findByStatusOrderByCreatedAtDesc(DeviceChangeRequest.RequestStatus status);
    List<DeviceChangeRequest> findAllByOrderByCreatedAtDesc();

    // Returns most recent request for a given student + status
    Optional<DeviceChangeRequest> findTopByStudentIdAndStatusOrderByCreatedAtDesc(
            Long studentId, DeviceChangeRequest.RequestStatus status);

    boolean existsByStudentIdAndStatus(Long studentId, DeviceChangeRequest.RequestStatus status);
}
