package com.aura.attendance.controller;

import com.aura.attendance.dto.MarkAttendanceRequest;
import com.aura.attendance.entity.*;
import com.aura.attendance.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final SemesterRepository semesterRepository;
    private final HolidayRepository holidayRepository;
    private final SubjectRepository subjectRepository;
    private final SectionAssignmentRepository assignmentRepository;
    private final NotificationRepository notificationRepository;
    private final SuspiciousActivityRepository suspiciousRepository;
    private final DeviceChangeRequestRepository deviceRequestRepository;

    public StudentController(UserRepository userRepository, SessionRepository sessionRepository,
                             AttendanceRepository attendanceRepository, SemesterRepository semesterRepository,
                             HolidayRepository holidayRepository, SubjectRepository subjectRepository,
                             SectionAssignmentRepository assignmentRepository,
                             NotificationRepository notificationRepository,
                             SuspiciousActivityRepository suspiciousRepository,
                             DeviceChangeRequestRepository deviceRequestRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.semesterRepository = semesterRepository;
        this.holidayRepository = holidayRepository;
        this.subjectRepository = subjectRepository;
        this.assignmentRepository = assignmentRepository;
        this.notificationRepository = notificationRepository;
        this.suspiciousRepository = suspiciousRepository;
        this.deviceRequestRepository = deviceRequestRepository;
    }

    private User getCurrentUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ---- Overview Stats ----
    @GetMapping("/overview")
    public ResponseEntity<?> overview(@AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        List<AttendanceRecord> records = attendanceRepository.findByStudentId(student.getId());
        long present = records.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT).count();
        long total = records.size();
        Optional<Semester> semOpt = semesterRepository.findByIsActiveTrue();
        Map<String, Object> res = new HashMap<>();
        res.put("present", present);
        res.put("total", total);
        res.put("semester", semOpt.map(s -> Map.of(
                "id", s.getId(), "name", s.getName(),
                "startDate", s.getStartDate().toString(), "endDate", s.getEndDate().toString()
        )).orElse(null));
        return ResponseEntity.ok(res);
    }

    // ---- Active session for student's section ----
    @GetMapping("/active-session")
    public ResponseEntity<?> getActiveSession(@AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        if (student.getDepartment() == null) return ResponseEntity.ok(Map.of());

        Optional<Session> session = sessionRepository.findByDepartmentAndYearAndSectionAndStatus(
                student.getDepartment(), student.getYear(), student.getSection(), Session.SessionStatus.ACTIVE);

        if (session.isEmpty()) return ResponseEntity.ok(Map.of());

        Session s = session.get();
        Map<String, Object> res = new HashMap<>();
        res.put("id", s.getId());
        res.put("subjectName", s.getSubject().getName());
        res.put("subjectCode", s.getSubject().getCode());
        res.put("department", s.getDepartment().name());
        res.put("year", s.getYear());
        res.put("section", s.getSection());

        // Short code: only show if teacher has revealed it AND it's not expired
        boolean codeActive = Boolean.TRUE.equals(s.getShortCodeRevealed())
                && s.getShortCode() != null
                && s.getShortCodeExpiresAt() != null
                && s.getShortCodeExpiresAt().isAfter(LocalDateTime.now());

        res.put("shortCodeActive", codeActive);
        // Do NOT send the actual code — student must enter it manually
        // (they see it on the projector/teacher screen)

        boolean alreadyMarked = attendanceRepository.existsBySessionIdAndStudentId(s.getId(), student.getId());
        res.put("alreadyMarked", alreadyMarked);

        return ResponseEntity.ok(res);
    }

    // ---- Mark attendance (QR or short code) ----
    @PostMapping("/mark-attendance")
    public ResponseEntity<?> markAttendance(@RequestBody MarkAttendanceRequest req,
                                             @AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);

        Optional<Session> sessionOpt = sessionRepository.findByDepartmentAndYearAndSectionAndStatus(
                student.getDepartment(), student.getYear(), student.getSection(), Session.SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "No active session for your class"));

        Session session = sessionOpt.get();

        if (attendanceRepository.existsBySessionIdAndStudentId(session.getId(), student.getId()))
            return ResponseEntity.badRequest().body(Map.of("error", "Attendance already marked"));

        boolean valid = false;

        if (req.getQrToken() != null && !req.getQrToken().isEmpty()) {
            // Token format: sessionId.first8OfSecret.epochSeconds
            String[] parts = req.getQrToken().split("\\.");
            if (parts.length == 3) {
                try {
                    long tokenSessionId = Long.parseLong(parts[0]);
                    String secretPrefix = parts[1];
                    long epochSec       = Long.parseLong(parts[2]);
                    long nowSec         = System.currentTimeMillis() / 1000;
                    String myPrefix     = session.getQrSecret().length() >= 8
                                            ? session.getQrSecret().substring(0, 8)
                                            : session.getQrSecret();
                    valid = tokenSessionId == session.getId()
                            && myPrefix.equals(secretPrefix)
                            && nowSec - epochSec < 75; // 60s window + 15s grace
                } catch (NumberFormatException ignore) { valid = false; }
            }
        } else if (req.getShortCode() != null && !req.getShortCode().isEmpty()) {
            // Validate short code — must be revealed, match, and not expired
            valid = Boolean.TRUE.equals(session.getShortCodeRevealed())
                    && req.getShortCode().equals(session.getShortCode())
                    && session.getShortCodeExpiresAt() != null
                    && session.getShortCodeExpiresAt().isAfter(LocalDateTime.now());
        }

        if (!valid) {
            suspiciousRepository.save(SuspiciousActivity.builder()
                    .student(student).session(session)
                    .description("Invalid attendance token used")
                    .severity(SuspiciousActivity.Severity.MEDIUM).build());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired code. Try again."));
        }

        // Fingerprint check — only if student has enrolled a device
        if (student.getDeviceFingerprint() != null && req.getFingerprint() != null
                && !student.getDeviceFingerprint().equals(req.getFingerprint())) {
            suspiciousRepository.save(SuspiciousActivity.builder()
                    .student(student).session(session)
                    .description("Device fingerprint mismatch during attendance marking")
                    .severity(SuspiciousActivity.Severity.HIGH).build());
            return ResponseEntity.badRequest().body(Map.of("error", "Device mismatch. Use your registered device or request a device change from admin."));
        }

        AttendanceRecord record = AttendanceRecord.builder()
                .session(session).student(student)
                .status(AttendanceRecord.AttendanceStatus.PRESENT)
                .deviceFingerprint(req.getFingerprint())
                .build();
        attendanceRepository.save(record);

        return ResponseEntity.ok(Map.of("success", true, "message", "Attendance marked successfully!"));
    }

    // ---- Subject-wise stats ----
    @GetMapping("/subject-stats")
    public ResponseEntity<?> subjectStats(@AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        List<Subject> subjects = subjectRepository.findByDepartmentAndYear(student.getDepartment(), student.getYear());
        List<AttendanceRecord> records = attendanceRepository.findByStudentId(student.getId());

        Map<Long, long[]> subjectMap = new HashMap<>();
        for (AttendanceRecord r : records) {
            Long subId = r.getSession().getSubject().getId();
            subjectMap.putIfAbsent(subId, new long[]{0, 0});
            subjectMap.get(subId)[1]++;
            if (r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT) subjectMap.get(subId)[0]++;
        }

        List<Map<String, Object>> result = subjects.stream().map(s -> {
            long[] stats = subjectMap.getOrDefault(s.getId(), new long[]{0, 0});
            double pct = stats[1] > 0 ? (stats[0] * 100.0 / stats[1]) : 0;
            return Map.<String, Object>of(
                    "id", s.getId(), "name", s.getName(), "code", s.getCode(), "credits", s.getCredits(),
                    "present", stats[0], "total", stats[1], "pct", Math.round(pct * 10.0) / 10.0
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ---- Attendance history ----
    @GetMapping("/history")
    public ResponseEntity<?> history(@AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        List<AttendanceRecord> records = attendanceRepository.findByStudentId(student.getId());
        List<Map<String, Object>> result = records.stream()
                .sorted(Comparator.comparing(AttendanceRecord::getMarkedAt).reversed())
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "subjectName", r.getSession().getSubject().getName(),
                        "subjectCode", r.getSession().getSubject().getCode(),
                        "teacherName", r.getSession().getTeacher().getFullName(),
                        "status", r.getStatus().name(),
                        "markedAt", r.getMarkedAt().toString()
                )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ---- My teachers ----
    @GetMapping("/teachers")
    public ResponseEntity<?> myTeachers(@AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        if (student.getDepartment() == null) return ResponseEntity.ok(List.of());

        List<SectionAssignment> assignments = assignmentRepository
                .findByDepartmentAndYear(student.getDepartment(), student.getYear())
                .stream()
                .filter(a -> a.getSection().equals(student.getSection()))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = assignments.stream().map(a -> {
            User teacher = a.getTeacher();
            Subject sub = a.getSubject();
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("teacherName", teacher.getFullName());
            m.put("teacherEmail", teacher.getEmail());
            m.put("facultyId", teacher.getFacultyId() != null ? teacher.getFacultyId() : "");
            m.put("subjectName", sub != null ? sub.getName() : "");
            m.put("subjectCode", sub != null ? sub.getCode() : "");
            m.put("credits", sub != null ? sub.getCredits() : 0);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ---- Device management ----
    @GetMapping("/device")
    public ResponseEntity<?> getDevice(@AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        boolean hasPendingRequest = deviceRequestRepository.existsByStudentIdAndStatus(
                student.getId(), DeviceChangeRequest.RequestStatus.PENDING);

        return ResponseEntity.ok(Map.of(
                "enrolled", student.getDeviceFingerprint() != null ? student.getDeviceFingerprint() : "",
                "hasPendingRequest", hasPendingRequest
        ));
    }

    @PutMapping("/device/enroll")
    public ResponseEntity<?> enrollDevice(@RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        // Only allow enroll if no device enrolled yet
        if (student.getDeviceFingerprint() != null && !student.getDeviceFingerprint().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Device already enrolled. Submit a change request if you want to switch devices."));
        }
        student.setDeviceFingerprint(body.get("fingerprint"));
        userRepository.save(student);
        return ResponseEntity.ok(Map.of("success", true, "message", "Device enrolled successfully!"));
    }

    // ---- Device change request ----
    @PostMapping("/device/request-change")
    public ResponseEntity<?> requestDeviceChange(@RequestBody Map<String, String> body,
                                                  @AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);

        // Can't have two pending requests at once
        if (deviceRequestRepository.existsByStudentIdAndStatus(student.getId(), DeviceChangeRequest.RequestStatus.PENDING)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "You already have a pending device change request. Wait for admin to respond."));
        }

        String newFp = body.get("fingerprint");
        String reason = body.getOrDefault("reason", "Device replacement");

        if (newFp == null || newFp.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Device fingerprint is required"));

        DeviceChangeRequest req = DeviceChangeRequest.builder()
                .student(student)
                .newFingerprint(newFp)
                .reason(reason)
                .status(DeviceChangeRequest.RequestStatus.PENDING)
                .build();

        deviceRequestRepository.save(req);
        return ResponseEntity.ok(Map.of("success", true, "message", "Request submitted! Admin will review it shortly."));
    }

    // ---- Get my device requests (so student can see status) ----
    @GetMapping("/device/requests")
    public ResponseEntity<?> getMyRequests(@AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        List<DeviceChangeRequest> requests = deviceRequestRepository
                .findByStudentIdOrderByCreatedAtDesc(student.getId());

        List<Map<String, Object>> result = requests.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("status", r.getStatus().name());
            m.put("reason", r.getReason() != null ? r.getReason() : "");
            m.put("adminNote", r.getAdminNote() != null ? r.getAdminNote() : "");
            m.put("createdAt", r.getCreatedAt().toString());
            m.put("resolvedAt", r.getResolvedAt() != null ? r.getResolvedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ---- Notifications ----
    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        List<Notification> notifs = notificationRepository.findByUserIdOrderByCreatedAtDesc(student.getId());
        long unread = notificationRepository.countByUserIdAndIsReadFalse(student.getId());
        return ResponseEntity.ok(Map.of("notifications", notifs, "unread", unread));
    }

    @PutMapping("/notifications/read-all")
    public ResponseEntity<?> markAllRead(@AuthenticationPrincipal UserDetails ud) {
        User student = getCurrentUser(ud);
        List<Notification> notifs = notificationRepository.findByUserIdOrderByCreatedAtDesc(student.getId());
        notifs.forEach(n -> { n.setIsRead(true); notificationRepository.save(n); });
        return ResponseEntity.ok(Map.of("success", true));
    }
}