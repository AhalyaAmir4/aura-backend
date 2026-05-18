package com.aura.attendance.controller;

import com.aura.attendance.dto.SessionStartRequest;
import com.aura.attendance.entity.*;
import com.aura.attendance.repository.*;
import com.aura.attendance.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final SectionAssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final SubjectRepository subjectRepository;
    private final EmailService emailService;
    private final SuspiciousActivityRepository suspiciousActivityRepository;
    private final NotificationRepository notificationRepository;

    public TeacherController(UserRepository userRepository, SessionRepository sessionRepository,
                             SectionAssignmentRepository assignmentRepository,
                             AttendanceRepository attendanceRepository,
                             SubjectRepository subjectRepository,
                             EmailService emailService,
                             SuspiciousActivityRepository suspiciousActivityRepository,
                             NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.assignmentRepository = assignmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.subjectRepository = subjectRepository;
        this.emailService = emailService;
        this.suspiciousActivityRepository = suspiciousActivityRepository;
        this.notificationRepository = notificationRepository;
    }

    private User getCurrentUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ── Allocations + active session ──
    @GetMapping("/allocations")
    public ResponseEntity<?> getAllocations(@AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        List<SectionAssignment> allocs = assignmentRepository.findByTeacherId(teacher.getId());

        Set<Long> seen = new HashSet<>();
        List<Map<String, Object>> subjects = allocs.stream()
                .filter(a -> a.getSubject() != null && seen.add(a.getSubject().getId()))
                .map(a -> {
                    Subject s = a.getSubject();
                    return Map.<String, Object>of(
                            "id", s.getId(), "name", s.getName(),
                            "code", s.getCode(), "year", s.getYear());
                }).collect(Collectors.toList());

        List<Map<String, Object>> allocList = allocs.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("subjectId", a.getSubject() != null ? a.getSubject().getId() : null);
            m.put("subjectName", a.getSubject() != null ? a.getSubject().getName() : "");
            m.put("subjectCode", a.getSubject() != null ? a.getSubject().getCode() : "");
            m.put("section", a.getSection());
            m.put("department", a.getDepartment().name());
            m.put("year", a.getYear());
            return m;
        }).collect(Collectors.toList());

        Optional<Session> activeSession = sessionRepository.findByTeacherIdAndStatus(
                teacher.getId(), Session.SessionStatus.ACTIVE);

        Map<String, Object> response = new HashMap<>();
        response.put("subjects", subjects);
        response.put("allocations", allocList);
        response.put("activeSession", activeSession.map(this::sessionToMap).orElse(null));
        return ResponseEntity.ok(response);
    }

    // ── All subjects (for free-pick when starting session) ──
    @GetMapping("/subjects")
    public ResponseEntity<?> getAllSubjects() {
        List<Subject> all = subjectRepository.findAllByOrderByDepartmentAscYearAsc();
        return ResponseEntity.ok(all.stream().map(s -> Map.of(
                "id", s.getId(), "name", s.getName(), "code", s.getCode(),
                "year", s.getYear(), "department", s.getDepartment().name(), "credits", s.getCredits()
        )).collect(Collectors.toList()));
    }

    // ── Start session — teacher can pick ANY dept/year/section ──
    @PostMapping("/sessions/start")
    public ResponseEntity<?> startSession(@RequestBody SessionStartRequest req,
                                           @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);

        if (sessionRepository.findByTeacherIdAndStatus(teacher.getId(), Session.SessionStatus.ACTIVE).isPresent())
            return ResponseEntity.badRequest().body(Map.of("error", "You already have an active session. End it first."));

        Subject subject = subjectRepository.findById(req.getSubjectId())
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        // Get department and year from subject (or from request if provided)
        User.Department dept = req.getDepartment() != null
                ? User.Department.valueOf(req.getDepartment())
                : subject.getDepartment();
        int year = req.getYear() != null ? req.getYear() : subject.getYear();

        Session session = Session.builder()
                .teacher(teacher)
                .subject(subject)
                .department(dept)
                .year(year)
                .section(req.getSection())
                .qrSecret(UUID.randomUUID().toString())
                .shortCodeRevealed(false)
                .status(Session.SessionStatus.ACTIVE)
                .build();

        return ResponseEntity.ok(sessionToMap(sessionRepository.save(session)));
    }

    // ── End session ──
    @PutMapping("/sessions/{id}/end")
    public ResponseEntity<?> endSession(@PathVariable Long id,
                                         @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getTeacher().getId().equals(teacher.getId()))
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));

        session.setStatus(Session.SessionStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);
        autoMarkAbsent(session);
        return ResponseEntity.ok(Map.of("ended", true));
    }

    // ── QR token ──
    @GetMapping("/sessions/{id}/qr")
    public ResponseEntity<?> getQr(@PathVariable Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        String secret8 = session.getQrSecret().length() >= 8 ? session.getQrSecret().substring(0, 8) : session.getQrSecret();
        String token = session.getId() + "." + secret8 + "." + (System.currentTimeMillis() / 1000);
        return ResponseEntity.ok(Map.of("token", token));
    }

    // ── Reveal short code ──
    @PostMapping("/sessions/{id}/reveal-code")
    public ResponseEntity<?> revealCode(@PathVariable Long id,
                                         @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getTeacher().getId().equals(teacher.getId()))
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));

        String code = String.valueOf((int)(1000 + Math.random() * 9000));
        session.setShortCode(code);
        session.setShortCodeExpiresAt(LocalDateTime.now().plusSeconds(30));
        session.setShortCodeRevealed(true);
        sessionRepository.save(session);
        return ResponseEntity.ok(Map.of("code", code, "expiresAt", session.getShortCodeExpiresAt().toString()));
    }

    // ── Hide short code ──
    @PostMapping("/sessions/{id}/hide-code")
    public ResponseEntity<?> hideCode(@PathVariable Long id,
                                       @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getTeacher().getId().equals(teacher.getId()))
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        session.setShortCodeRevealed(false);
        session.setShortCode(null);
        session.setShortCodeExpiresAt(null);
        sessionRepository.save(session);
        return ResponseEntity.ok(Map.of("hidden", true));
    }

    // ── Manual mark PRESENT ──
    @PostMapping("/sessions/{id}/mark/{studentId}")
    @Transactional
    public ResponseEntity<?> manualMark(@PathVariable Long id, @PathVariable Long studentId,
                                         @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        Session session = sessionRepository.findById(id).orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getTeacher().getId().equals(teacher.getId()))
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        User student = userRepository.findById(studentId).orElseThrow(() -> new RuntimeException("Student not found"));

        Optional<AttendanceRecord> existing = attendanceRepository.findBySessionIdAndStudentId(id, studentId);
        if (existing.isPresent()) {
            AttendanceRecord rec = existing.get();
            rec.setStatus(AttendanceRecord.AttendanceStatus.PRESENT);
            rec.setDeviceFingerprint("MANUAL_BY_TEACHER");
            rec.setOdReason(null);
            attendanceRepository.save(rec);
        } else {
            attendanceRepository.save(AttendanceRecord.builder()
                    .session(session).student(student)
                    .status(AttendanceRecord.AttendanceStatus.PRESENT)
                    .deviceFingerprint("MANUAL_BY_TEACHER").build());
        }
        return ResponseEntity.ok(Map.of("success", true, "message", student.getFullName() + " marked present"));
    }

    // ── Manual unmark ──
    @PostMapping("/sessions/{id}/unmark/{studentId}")
    @Transactional
    public ResponseEntity<?> manualUnmark(@PathVariable Long id, @PathVariable Long studentId,
                                           @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        Session session = sessionRepository.findById(id).orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getTeacher().getId().equals(teacher.getId()))
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));

        Optional<AttendanceRecord> existing = attendanceRepository.findBySessionIdAndStudentId(id, studentId);
        if (existing.isPresent()) {
            AttendanceRecord rec = existing.get();
            String fp = rec.getDeviceFingerprint();
            if ("MANUAL_BY_TEACHER".equals(fp) || "OD_BY_TEACHER".equals(fp)) {
                rec.setStatus(AttendanceRecord.AttendanceStatus.ABSENT);
                rec.setOdReason(null);
                attendanceRepository.save(rec);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot undo — student self-marked"));
            }
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Flag student as suspicious → set ABSENT + notify student ──
    @PostMapping("/sessions/{id}/flag/{studentId}")
    @Transactional
    public ResponseEntity<?> flagStudent(@PathVariable Long id,
                                          @PathVariable Long studentId,
                                          @RequestBody Map<String, Object> body,
                                          @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getTeacher().getId().equals(teacher.getId()))
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        String reason = Objects.toString(body.get("reason"), "Suspicious behavior observed");

        // 1. Set attendance to ABSENT (or update existing record)
        Optional<AttendanceRecord> existing = attendanceRepository
                .findBySessionIdAndStudentId(id, studentId);
        if (existing.isPresent()) {
            AttendanceRecord rec = existing.get();
            rec.setStatus(AttendanceRecord.AttendanceStatus.ABSENT);
            rec.setOdReason(null);
            attendanceRepository.save(rec);
        } else {
            // No record yet — create an ABSENT one
            attendanceRepository.save(AttendanceRecord.builder()
                    .student(student)
                    .session(session)
                    .status(AttendanceRecord.AttendanceStatus.ABSENT)
                    .deviceFingerprint("FLAGGED_BY_TEACHER")
                    .markedAt(java.time.LocalDateTime.now())
                    .build());
        }

        // 2. Save suspicious activity record
        suspiciousActivityRepository.save(SuspiciousActivity.builder()
                .student(student)
                .session(session)
                .description("Teacher flagged: " + reason)
                .severity(SuspiciousActivity.Severity.HIGH)
                .build());

        // 3. Send in-app notification to student
        String subjectName = session.getSubject() != null ? session.getSubject().getName() : "class";
        notificationRepository.save(Notification.builder()
                .user(student)
                .title("Attendance Marked Absent")
                .body("Your attendance for " + subjectName + " has been marked ABSENT by your teacher. "
                    + "Reason: " + reason + ". "
                    + "Contact your teacher if this is incorrect.")
                .kind("warning")
                .build());

        return ResponseEntity.ok(Map.of("success", true,
                "message", "Student marked absent, flagged as suspicious, and notified"));
    }

    // ── Grant OD ──
    @PostMapping("/sessions/{id}/od/{studentId}")
    @Transactional
    public ResponseEntity<?> grantOd(@PathVariable Long id, @PathVariable Long studentId,
                                      @RequestBody(required = false) Map<String, String> body,
                                      @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found: " + id));
        if (!session.getTeacher().getId().equals(teacher.getId()))
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
        String reason = (body != null && body.get("reason") != null && !body.get("reason").isBlank())
                ? body.get("reason") : "On Duty";

        log.info("[OD] Teacher {} granting OD to student {} for session {}, reason='{}'",
                teacher.getEmail(), student.getEmail(), id, reason);

        Optional<AttendanceRecord> existing = attendanceRepository.findBySessionIdAndStudentId(id, studentId);
        if (existing.isPresent()) {
            AttendanceRecord rec = existing.get();
            rec.setStatus(AttendanceRecord.AttendanceStatus.OD);
            rec.setOdReason(reason);
            rec.setDeviceFingerprint("OD_BY_TEACHER");
            attendanceRepository.save(rec);
            log.info("[OD] Updated existing record id={} to OD", rec.getId());
        } else {
            AttendanceRecord saved = attendanceRepository.save(AttendanceRecord.builder()
                    .session(session).student(student)
                    .status(AttendanceRecord.AttendanceStatus.OD)
                    .odReason(reason)
                    .deviceFingerprint("OD_BY_TEACHER")
                    .markedAt(LocalDateTime.now())
                    .build());
            log.info("[OD] Created new OD record id={}", saved.getId());
        }
        return ResponseEntity.ok(Map.of("success", true, "message", student.getFullName() + " granted OD"));
    }

    // ── Send absence email to parents after session ends ──
    @PostMapping("/sessions/{id}/send-absence-emails")
    @Transactional
    public ResponseEntity<?> sendAbsenceEmails(@PathVariable Long id,
                                               @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found: " + id));
        if (!session.getTeacher().getId().equals(teacher.getId()))
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));

        // Fetch all attendance records for this session
        List<AttendanceRecord> records = attendanceRepository.findBySessionId(id);
        Map<Long, AttendanceRecord> recMap = records.stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));

        // Get all students in this section
        List<User> students = userRepository.findByDepartmentAndYearAndSection(
                session.getDepartment(), session.getYear(), session.getSection());

        String subjectName = session.getSubject() != null ? session.getSubject().getName() : "Unknown Subject";
        String teacherName = teacher.getFullName();
        String date = session.getStartedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));

        int sent = 0, skipped = 0, failed = 0;
        List<String> sentTo = new ArrayList<>();

        for (User student : students) {
            AttendanceRecord rec = recMap.get(student.getId());
            // Only email parents of ABSENT students
            boolean isAbsent = (rec == null) || rec.getStatus() == AttendanceRecord.AttendanceStatus.ABSENT;
            if (!isAbsent) { skipped++; continue; }

            String parentEmail = student.getParentEmail();
            if (parentEmail == null || parentEmail.isBlank()) {
                log.warn("[AbsenceEmail] No parent email for student {}", student.getRollNumber());
                failed++;
                continue;
            }
            try {
                emailService.sendAbsenceAlert(
                        parentEmail,
                        student.getFullName(),
                        student.getRollNumber() != null ? student.getRollNumber() : student.getEmail(),
                        subjectName, teacherName, date);
                sent++;
                sentTo.add(student.getFullName());
            } catch (Exception e) {
                log.error("[AbsenceEmail] Failed for student {}: {}", student.getRollNumber(), e.getMessage());
                failed++;
            }
        }

        log.info("[AbsenceEmail] Session {} — sent={}, skipped(present/OD)={}, failed(no email/error)={}", id, sent, skipped, failed);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sent", sent,
                "skipped", skipped,
                "failed", failed,
                "sentTo", sentTo,
                "message", sent + " absence alert(s) dispatched."
        ));
    }

    // ── Session history list ──
    @GetMapping("/sessions/history")
    public ResponseEntity<?> getHistory(@AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        List<Session> sessions = sessionRepository.findByTeacherIdOrderByStartedAtDesc(teacher.getId());
        return ResponseEntity.ok(sessions.stream().map(this::sessionToMap).collect(Collectors.toList()));
    }

    // ── Full session detail with complete roster ──
    @GetMapping("/sessions/{id}/detail")
    public ResponseEntity<?> getSessionDetail(@PathVariable Long id,
                                               @AuthenticationPrincipal UserDetails ud) {
        User teacher = getCurrentUser(ud);
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getTeacher().getId().equals(teacher.getId()))
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));

        List<User> students = userRepository.findByDepartmentAndYearAndSection(
                session.getDepartment(), session.getYear(), session.getSection());
        List<AttendanceRecord> records = attendanceRepository.findBySessionId(id);
        Map<Long, AttendanceRecord> recMap = records.stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));

        List<Map<String, Object>> roster = students.stream().map(s -> {
            AttendanceRecord rec = recMap.get(s.getId());
            String status = rec != null ? rec.getStatus().name() : "ABSENT";
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("fullName", s.getFullName());
            m.put("rollNumber", s.getRollNumber() != null ? s.getRollNumber() : "");
            m.put("status", status);
            m.put("odReason", rec != null && rec.getOdReason() != null ? rec.getOdReason() : "");
            m.put("manual", rec != null && "MANUAL_BY_TEACHER".equals(rec.getDeviceFingerprint()));
            m.put("od", rec != null && "OD_BY_TEACHER".equals(rec.getDeviceFingerprint()));
            m.put("markedAt", rec != null && rec.getMarkedAt() != null ? rec.getMarkedAt().toString() : "");
            return m;
        }).collect(Collectors.toList());

        long present = roster.stream().filter(r -> "PRESENT".equals(r.get("status"))).count();
        long absent  = roster.stream().filter(r -> "ABSENT".equals(r.get("status"))).count();
        long od      = roster.stream().filter(r -> "OD".equals(r.get("status"))).count();

        Map<String, Object> result = new HashMap<>();
        result.put("session", sessionToMap(session));
        result.put("roster", roster);
        result.put("summary", Map.of("present", present, "absent", absent, "od", od, "total", students.size()));
        return ResponseEntity.ok(result);
    }

    // ── Live roster ──
    @GetMapping("/sessions/{id}/roster")
    public ResponseEntity<?> getRoster(@PathVariable Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        List<User> students = userRepository.findByDepartmentAndYearAndSection(
                session.getDepartment(), session.getYear(), session.getSection());
        List<AttendanceRecord> records = attendanceRepository.findBySessionId(id);
        Map<Long, AttendanceRecord> recMap = records.stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));

        List<Map<String, Object>> roster = students.stream().map(s -> {
            AttendanceRecord rec = recMap.get(s.getId());
            boolean present = rec != null && rec.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT;
            boolean od = rec != null && rec.getStatus() == AttendanceRecord.AttendanceStatus.OD;
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("fullName", s.getFullName());
            m.put("rollNumber", s.getRollNumber() != null ? s.getRollNumber() : "");
            m.put("present", present || od);
            m.put("od", od);
            m.put("manuallyMarked", rec != null && "MANUAL_BY_TEACHER".equals(rec.getDeviceFingerprint()));
            return m;
        }).collect(Collectors.toList());

        long presentCount = roster.stream().filter(r -> Boolean.TRUE.equals(r.get("present"))).count();
        return ResponseEntity.ok(Map.of("roster", roster, "presentCount", presentCount, "total", students.size()));
    }

    // ── Students list ──
    @GetMapping("/students")
    public ResponseEntity<?> getStudents(@RequestParam(required = false) String department,
                                          @RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) String section) {
        if (department == null || year == null || section == null) return ResponseEntity.ok(List.of());
        User.Department dept = User.Department.valueOf(department);
        return ResponseEntity.ok(userRepository.findByDepartmentAndYearAndSection(dept, year, section));
    }

    // ── Feed ──
    @GetMapping("/sessions/{id}/feed")
    public ResponseEntity<?> getFeed(@PathVariable Long id) {
        List<AttendanceRecord> records = attendanceRepository.findBySessionId(id);
        List<Map<String, Object>> feed = records.stream()
                .sorted(Comparator.comparing(AttendanceRecord::getMarkedAt).reversed())
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("studentName", r.getStudent().getFullName());
                    m.put("rollNumber", r.getStudent().getRollNumber() != null ? r.getStudent().getRollNumber() : "");
                    m.put("status", r.getStatus().name());
                    m.put("markedAt", r.getMarkedAt().toString());
                    m.put("manual", "MANUAL_BY_TEACHER".equals(r.getDeviceFingerprint()));
                    m.put("od", "OD_BY_TEACHER".equals(r.getDeviceFingerprint()));
                    m.put("odReason", r.getOdReason() != null ? r.getOdReason() : "");
                    return m;
                }).collect(Collectors.toList());
        return ResponseEntity.ok(feed);
    }

    private Map<String, Object> sessionToMap(Session s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("department", s.getDepartment().name());
        map.put("year", s.getYear());
        map.put("section", s.getSection());
        map.put("status", s.getStatus().name());
        map.put("startedAt", s.getStartedAt().toString());
        map.put("endedAt", s.getEndedAt() != null ? s.getEndedAt().toString() : null);
        map.put("shortCodeRevealed", Boolean.TRUE.equals(s.getShortCodeRevealed()));
        map.put("shortCode", s.getShortCode() != null ? s.getShortCode() : "");
        map.put("shortCodeExpiresAt", s.getShortCodeExpiresAt() != null ? s.getShortCodeExpiresAt().toString() : null);
        if (s.getSubject() != null) {
            map.put("subjectId", s.getSubject().getId());
            map.put("subjectName", s.getSubject().getName());
            map.put("subjectCode", s.getSubject().getCode());
        }
        return map;
    }

    private void autoMarkAbsent(Session session) {
        List<User> students = userRepository.findByDepartmentAndYearAndSection(
                session.getDepartment(), session.getYear(), session.getSection());
        LocalDateTime now = LocalDateTime.now();
        for (User student : students) {
            if (!attendanceRepository.existsBySessionIdAndStudentId(session.getId(), student.getId())) {
                attendanceRepository.save(AttendanceRecord.builder()
                        .session(session).student(student)
                        .status(AttendanceRecord.AttendanceStatus.ABSENT)
                        .markedAt(now)
                        .build());
            }
        }
    }
}