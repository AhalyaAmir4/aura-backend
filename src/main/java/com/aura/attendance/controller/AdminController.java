package com.aura.attendance.controller;

import com.aura.attendance.dto.*;
import com.aura.attendance.entity.*;
import com.aura.attendance.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final HolidayRepository holidayRepository;
    private final SubjectRepository subjectRepository;
    private final SectionAssignmentRepository assignmentRepository;
    private final RegistrationSlotRepository slotRepository;
    private final SuspiciousActivityRepository suspiciousRepository;
    private final AttendanceRepository attendanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceChangeRequestRepository deviceRequestRepository;
    private final NotificationRepository notificationRepository;

    public AdminController(UserRepository userRepository, SemesterRepository semesterRepository,
                           HolidayRepository holidayRepository, SubjectRepository subjectRepository,
                           SectionAssignmentRepository assignmentRepository,
                           RegistrationSlotRepository slotRepository,
                           SuspiciousActivityRepository suspiciousRepository,
                           AttendanceRepository attendanceRepository,
                           PasswordEncoder passwordEncoder,
                           DeviceChangeRequestRepository deviceRequestRepository,
                           NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.semesterRepository = semesterRepository;
        this.holidayRepository = holidayRepository;
        this.subjectRepository = subjectRepository;
        this.assignmentRepository = assignmentRepository;
        this.slotRepository = slotRepository;
        this.suspiciousRepository = suspiciousRepository;
        this.attendanceRepository = attendanceRepository;
        this.passwordEncoder = passwordEncoder;
        this.deviceRequestRepository = deviceRequestRepository;
        this.notificationRepository = notificationRepository;
    }

    // ========== OVERVIEW ==========
    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        long students = userRepository.countByRole(User.AppRole.student);
        long teachers = userRepository.countByRole(User.AppRole.teacher);
        long suspicious = suspiciousRepository.count();
        long pendingDeviceRequests = deviceRequestRepository
                .findByStatusOrderByCreatedAtDesc(DeviceChangeRequest.RequestStatus.PENDING).size();
        String semester = semesterRepository.findByIsActiveTrue()
                .map(Semester::getName).orElse("None");

        return ResponseEntity.ok(Map.of(
                "students", students,
                "teachers", teachers,
                "suspicious", suspicious,
                "semester", semester,
                "pendingDeviceRequests", pendingDeviceRequests
        ));
    }

    // ========== SEMESTERS ==========
    @GetMapping("/semesters")
    public ResponseEntity<?> getSemesters() {
        return ResponseEntity.ok(semesterRepository.findAllByOrderByStartDateDesc());
    }

    @PostMapping("/semesters")
    public ResponseEntity<?> createSemester(@RequestBody SemesterRequest req) {
        Semester semester = Semester.builder()
                .name(req.getName()).academicYear(req.getAcademicYear())
                .semesterType(req.getSemesterType())
                .startDate(req.getStartDate()).endDate(req.getEndDate())
                .isActive(false).build();
        return ResponseEntity.ok(semesterRepository.save(semester));
    }

    @PutMapping("/semesters/{id}/activate")
    public ResponseEntity<?> activateSemester(@PathVariable Long id) {
        semesterRepository.findAll().forEach(s -> { s.setIsActive(false); semesterRepository.save(s); });
        Semester s = semesterRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        s.setIsActive(true);
        return ResponseEntity.ok(semesterRepository.save(s));
    }

    @DeleteMapping("/semesters/{id}")
    public ResponseEntity<?> deleteSemester(@PathVariable Long id) {
        semesterRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ========== HOLIDAYS ==========
    @GetMapping("/holidays")
    public ResponseEntity<?> getHolidays(@RequestParam Long semesterId) {
        return ResponseEntity.ok(holidayRepository.findBySemesterIdOrderByHolidayDate(semesterId));
    }

    @PostMapping("/holidays")
    public ResponseEntity<?> createHoliday(@RequestBody HolidayRequest req) {
        Semester sem = semesterRepository.findById(req.getSemesterId())
                .orElseThrow(() -> new RuntimeException("Semester not found"));
        Holiday holiday = Holiday.builder()
                .semester(sem).holidayDate(req.getHolidayDate())
                .description(req.getDescription()).holidayType(req.getHolidayType()).build();
        return ResponseEntity.ok(holidayRepository.save(holiday));
    }

    @PostMapping("/holidays/seed-sundays")
    public ResponseEntity<?> seedSundays(@RequestParam Long semesterId) {
        Semester sem = semesterRepository.findById(semesterId)
                .orElseThrow(() -> new RuntimeException("Semester not found"));
        LocalDate d = sem.getStartDate();
        int count = 0;
        while (!d.isAfter(sem.getEndDate())) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                LocalDate finalD = d;
                boolean exists = holidayRepository.findBySemesterIdOrderByHolidayDate(semesterId)
                        .stream().anyMatch(h -> h.getHolidayDate().equals(finalD));
                if (!exists) {
                    holidayRepository.save(Holiday.builder()
                            .semester(sem).holidayDate(d)
                            .description("Sunday").holidayType(Holiday.HolidayType.COLLEGE).build());
                    count++;
                }
            }
            d = d.plusDays(1);
        }
        return ResponseEntity.ok(Map.of("added", count));
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<?> deleteHoliday(@PathVariable Long id) {
        holidayRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ========== SUBJECTS ==========
    @GetMapping("/subjects")
    public ResponseEntity<?> getSubjects() {
        return ResponseEntity.ok(subjectRepository.findAllByOrderByDepartmentAscYearAsc());
    }

    @PostMapping("/subjects")
    public ResponseEntity<?> createSubject(@RequestBody SubjectRequest req) {
        Subject s = Subject.builder().name(req.getName()).code(req.getCode())
                .credits(req.getCredits()).department(req.getDepartment()).year(req.getYear()).build();
        return ResponseEntity.ok(subjectRepository.save(s));
    }

    @DeleteMapping("/subjects/{id}")
    public ResponseEntity<?> deleteSubject(@PathVariable Long id) {
        subjectRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ========== SECTION ASSIGNMENTS ==========
    @GetMapping("/sections")
    public ResponseEntity<?> getSections(@RequestParam String department, @RequestParam Integer year) {
        User.Department dept = User.Department.valueOf(department);
        List<SectionAssignment> assignments = assignmentRepository.findByDepartmentAndYear(dept, year);
        List<Subject> subjects = subjectRepository.findByDepartmentAndYear(dept, year);
        List<User> teachers = userRepository.findByRole(User.AppRole.teacher);

        return ResponseEntity.ok(Map.of(
                "assignments", assignments,
                "subjects", subjects,
                "teachers", teachers.stream().map(t -> Map.of(
                        "id", t.getId(), "fullName", t.getFullName(),
                        "facultyId", t.getFacultyId() != null ? t.getFacultyId() : "",
                        "department", t.getDepartment() != null ? t.getDepartment().name() : "",
                        "email", t.getEmail()
                )).collect(Collectors.toList())
        ));
    }

    @PostMapping("/sections/assign")
    public ResponseEntity<?> assignSection(@RequestBody SectionAssignRequest req) {
        User teacher = userRepository.findById(req.getTeacherId())
                .orElseThrow(() -> new RuntimeException("Teacher not found"));
        Subject subject = subjectRepository.findById(req.getSubjectId())
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        Optional<SectionAssignment> existing = assignmentRepository
                .findByDepartmentAndYearAndSectionAndSubjectId(
                        req.getDepartment(), req.getYear(), req.getSection(), req.getSubjectId());

        if (existing.isPresent()) {
            SectionAssignment a = existing.get();
            a.setTeacher(teacher);
            return ResponseEntity.ok(assignmentRepository.save(a));
        } else {
            SectionAssignment a = SectionAssignment.builder()
                    .teacher(teacher).subject(subject)
                    .department(req.getDepartment()).year(req.getYear()).section(req.getSection()).build();
            return ResponseEntity.ok(assignmentRepository.save(a));
        }
    }

    @DeleteMapping("/sections/{id}")
    public ResponseEntity<?> deleteAssignment(@PathVariable Long id) {
        assignmentRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ========== STUDENTS ==========
    @GetMapping("/students")
    public ResponseEntity<?> getStudents(@RequestParam String department,
                                          @RequestParam Integer year,
                                          @RequestParam String section) {
        User.Department dept = User.Department.valueOf(department);
        return ResponseEntity.ok(userRepository.findByDepartmentAndYearAndSection(dept, year, section));
    }

    @PostMapping("/students/create")
    public ResponseEntity<?> createStudents(@RequestBody AdminCreateUserRequest req) {
        List<Map<String, Object>> results = new ArrayList<>();
        int created = 0;
        for (AdminCreateUserRequest.StudentEntry s : req.getStudents()) {
            try {
                String namePart = s.getFullName().toLowerCase()
                        .replaceAll("[^a-z0-9]", "")
                        .substring(0, Math.min(10, s.getFullName().replaceAll("[^a-zA-Z0-9]", "").length()));
                String rollPart = s.getRollNumber().toLowerCase().replaceAll("[^a-z0-9]", "");
                String email = namePart + rollPart.substring(0, Math.min(4, rollPart.length())) + "@" + req.getDomain();

                if (userRepository.existsByEmail(email)) {
                    results.add(Map.of("ok", false, "roll", s.getRollNumber(), "error", "Email already exists"));
                    continue;
                }
                User user = User.builder()
                        .fullName(s.getFullName()).email(email)
                        .password(passwordEncoder.encode(s.getRollNumber()))
                        .role(User.AppRole.student)
                        .department(req.getDepartment()).year(req.getYear())
                        .section(req.getSection()).rollNumber(s.getRollNumber())
                        .parentEmail(s.getParentEmail()).phone(s.getPhone()).build();
                userRepository.save(user);
                created++;
                results.add(Map.of("ok", true, "roll", s.getRollNumber(), "email", email, "password", s.getRollNumber()));
            } catch (Exception e) {
                results.add(Map.of("ok", false, "roll", s.getRollNumber(), "error", e.getMessage()));
            }
        }
        return ResponseEntity.ok(Map.of("created", created, "total", req.getStudents().size(), "results", results));
    }

    // ========== TEACHERS ==========
    @GetMapping("/teachers")
    public ResponseEntity<?> getTeachers() {
        return ResponseEntity.ok(userRepository.findByRole(User.AppRole.teacher));
    }

    @PostMapping("/teachers/create")
    public ResponseEntity<?> createTeachers(@RequestBody AdminCreateUserRequest req) {
        List<Map<String, Object>> results = new ArrayList<>();
        int created = 0;
        for (AdminCreateUserRequest.TeacherEntry t : req.getTeachers()) {
            try {
                String namePart = t.getFullName().toLowerCase().replaceAll("[^a-z ]", "");
                String[] parts = namePart.trim().split(" ");
                String email = String.join("", parts) + "."
                        + (req.getDepartment() != null ? req.getDepartment().name().toLowerCase() : "staff")
                        + "@" + req.getDomain();

                if (userRepository.existsByEmail(email)) {
                    results.add(Map.of("ok", false, "facultyId", t.getFacultyId(), "error", "Email exists"));
                    continue;
                }
                User user = User.builder()
                        .fullName(t.getFullName()).email(email)
                        .password(passwordEncoder.encode(t.getFacultyId()))
                        .role(User.AppRole.teacher)
                        .department(req.getDepartment())
                        .facultyId(t.getFacultyId()).phone(t.getPhone()).build();
                userRepository.save(user);
                created++;
                results.add(Map.of("ok", true, "facultyId", t.getFacultyId(), "email", email, "password", t.getFacultyId()));
            } catch (Exception e) {
                results.add(Map.of("ok", false, "facultyId", t.getFacultyId(), "error", e.getMessage()));
            }
        }
        return ResponseEntity.ok(Map.of("created", created, "total", req.getTeachers().size(), "results", results));
    }

    // ========== REGISTRATION SLOTS ==========
    @GetMapping("/slots")
    public ResponseEntity<?> getSlots() {
        return ResponseEntity.ok(slotRepository.findAllByOrderByStartTimeDesc());
    }

    @PostMapping("/slots")
    public ResponseEntity<?> createSlot(@RequestBody Map<String, String> body) {
        RegistrationSlot slot = RegistrationSlot.builder()
                .startTime(LocalDateTime.parse(body.get("startTime")))
                .endTime(LocalDateTime.parse(body.get("endTime")))
                .isActive(true).build();
        return ResponseEntity.ok(slotRepository.save(slot));
    }

    @DeleteMapping("/slots/{id}")
    public ResponseEntity<?> deleteSlot(@PathVariable Long id) {
        slotRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ========== SUSPICIOUS ACTIVITY ==========
    @GetMapping("/suspicious")
    public ResponseEntity<?> getSuspicious() {
        return ResponseEntity.ok(suspiciousRepository.findAllByOrderByCreatedAtDesc());
    }

    // ========== DEVICE CHANGE REQUESTS ==========
    @GetMapping("/device-requests")
    public ResponseEntity<?> getDeviceRequests(@RequestParam(required = false) String status) {
        List<DeviceChangeRequest> requests;
        if (status != null) {
            requests = deviceRequestRepository.findByStatusOrderByCreatedAtDesc(
                    DeviceChangeRequest.RequestStatus.valueOf(status.toUpperCase()));
        } else {
            requests = deviceRequestRepository.findAllByOrderByCreatedAtDesc();
        }

        List<Map<String, Object>> result = requests.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("status", r.getStatus().name());
            m.put("reason", r.getReason() != null ? r.getReason() : "");
            m.put("adminNote", r.getAdminNote() != null ? r.getAdminNote() : "");
            m.put("newFingerprint", r.getNewFingerprint());
            m.put("createdAt", r.getCreatedAt().toString());
            m.put("resolvedAt", r.getResolvedAt() != null ? r.getResolvedAt().toString() : null);
            // Student info
            User student = r.getStudent();
            m.put("studentId", student.getId());
            m.put("studentName", student.getFullName());
            m.put("studentRoll", student.getRollNumber() != null ? student.getRollNumber() : "");
            m.put("studentEmail", student.getEmail());
            m.put("currentFingerprint", student.getDeviceFingerprint() != null ? student.getDeviceFingerprint() : "");
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PutMapping("/device-requests/{id}/approve")
    @Transactional
    public ResponseEntity<?> approveDeviceRequest(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, String> body) {
        log.info("[DeviceRequest] Admin approving request id={}", id);
        DeviceChangeRequest req = deviceRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device change request not found: " + id));

        if (req.getStatus() != DeviceChangeRequest.RequestStatus.PENDING) {
            log.warn("[DeviceRequest] Request {} already resolved (status={})", id, req.getStatus());
            return ResponseEntity.badRequest().body(Map.of("error", "Request already resolved with status: " + req.getStatus().name()));
        }

        // Update student's device fingerprint
        User student = req.getStudent();
        String oldFp = student.getDeviceFingerprint();
        student.setDeviceFingerprint(req.getNewFingerprint());
        userRepository.save(student);

        // Update request status
        String note = (body != null && body.get("note") != null && !body.get("note").isBlank())
                ? body.get("note") : "Approved by admin";
        req.setStatus(DeviceChangeRequest.RequestStatus.APPROVED);
        req.setAdminNote(note);
        req.setResolvedAt(LocalDateTime.now());
        deviceRequestRepository.save(req);

        log.info("[DeviceRequest] Approved. Student {} fingerprint updated: {} -> {}",
                student.getEmail(), oldFp != null ? oldFp.substring(0, 8) + "…" : "none",
                req.getNewFingerprint().substring(0, 8) + "…");

        // Notify student
        notificationRepository.save(Notification.builder()
                .user(student)
                .title("Device Change Approved")
                .body("Your request to change your registered device has been approved. You can now mark attendance from your new device.")
                .kind("success")
                .isRead(false)
                .build());

        return ResponseEntity.ok(Map.of("success", true, "message", "Device updated and student notified"));
    }

    @PutMapping("/device-requests/{id}/decline")
    @Transactional
    public ResponseEntity<?> declineDeviceRequest(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, String> body) {
        log.info("[DeviceRequest] Admin declining request id={}", id);
        DeviceChangeRequest req = deviceRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device change request not found: " + id));

        if (req.getStatus() != DeviceChangeRequest.RequestStatus.PENDING) {
            log.warn("[DeviceRequest] Request {} already resolved (status={})", id, req.getStatus());
            return ResponseEntity.badRequest().body(Map.of("error", "Request already resolved with status: " + req.getStatus().name()));
        }

        String note = (body != null && body.get("note") != null && !body.get("note").isBlank())
                ? body.get("note") : "Declined by admin";
        req.setStatus(DeviceChangeRequest.RequestStatus.DECLINED);
        req.setAdminNote(note);
        req.setResolvedAt(LocalDateTime.now());
        deviceRequestRepository.save(req);

        // Notify student
        User student = req.getStudent();
        notificationRepository.save(Notification.builder()
                .user(student)
                .title("Device Change Declined")
                .body("Your device change request was declined." + (note.isBlank() ? "" : " Reason: " + note))
                .kind("error")
                .isRead(false)
                .build());

        log.info("[DeviceRequest] Declined for student {}", student.getEmail());
        return ResponseEntity.ok(Map.of("success", true, "message", "Request declined and student notified"));
    }
}
