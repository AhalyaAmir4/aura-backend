# AURA Attendance System — Spring Boot Backend

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8.0+

### Setup

**1. Create MySQL database:**
```sql
CREATE DATABASE aura_attendance;
```

**2. Configure database credentials in `src/main/resources/application.properties`:**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/aura_attendance?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD
```

**3. Run the application:**
```bash
mvn spring-boot:run
```
Server starts on **http://localhost:8080**

---

## 📡 API Endpoints

### Auth (Public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | Login → returns JWT token |
| POST | `/api/auth/register` | Register (student/teacher/admin) |

### Admin (Role: ADMIN)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/overview` | Dashboard stats |
| GET/POST | `/api/admin/semesters` | List/create semesters |
| PUT | `/api/admin/semesters/{id}/activate` | Activate semester |
| DELETE | `/api/admin/semesters/{id}` | Delete semester |
| GET/POST | `/api/admin/holidays` | List/add holidays |
| POST | `/api/admin/holidays/seed-sundays` | Auto-add Sundays |
| DELETE | `/api/admin/holidays/{id}` | Delete holiday |
| GET/POST/DELETE | `/api/admin/subjects` | Manage subjects |
| GET | `/api/admin/sections` | Get section assignments |
| POST | `/api/admin/sections/assign` | Assign teacher to section |
| DELETE | `/api/admin/sections/{id}` | Remove assignment |
| GET | `/api/admin/students` | List students by class |
| POST | `/api/admin/students/create` | Bulk create student accounts |
| GET | `/api/admin/teachers` | List all teachers |
| POST | `/api/admin/teachers/create` | Bulk create teacher accounts |
| GET/POST/DELETE | `/api/admin/slots` | Registration slots |
| GET | `/api/admin/suspicious` | View suspicious activities |

### Teacher (Role: TEACHER)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/teacher/allocations` | Get my subjects/active session |
| POST | `/api/teacher/sessions/start` | Start a class session |
| PUT | `/api/teacher/sessions/{id}/end` | End session (auto-marks absent) |
| GET | `/api/teacher/sessions/{id}/qr` | Get current QR token |
| POST | `/api/teacher/sessions/{id}/reveal-code` | Reveal 4-digit short code |
| GET | `/api/teacher/sessions/history` | Session history |
| GET | `/api/teacher/sessions/{id}/roster` | Live attendance roster |
| GET | `/api/teacher/sessions/{id}/feed` | Attendance feed |
| GET | `/api/teacher/students` | My students list |

### Student (Role: STUDENT)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/student/overview` | Attendance overview stats |
| GET | `/api/student/active-session` | Get active session for my class |
| POST | `/api/student/mark-attendance` | Mark attendance (QR or code) |
| GET | `/api/student/subject-stats` | Subject-wise attendance |
| GET | `/api/student/history` | Attendance history |
| GET | `/api/student/teachers` | My teachers list |
| GET/PUT | `/api/student/device` | Device fingerprint management |
| GET/PUT | `/api/student/notifications` | Notifications |

---

## 🔐 Authentication

All secured endpoints require:
```
Authorization: Bearer <JWT_TOKEN>
```

JWT is returned from `/api/auth/login` or `/api/auth/register`.

---

## 🗄️ Database Schema

Tables created automatically by Hibernate (`ddl-auto=update`):
- `users` — all roles (admin/teacher/student)
- `semesters` — academic semesters
- `holidays` — holiday calendar per semester
- `subjects` — course subjects
- `section_assignments` — teacher-subject-section allocations
- `sessions` — live attendance sessions
- `attendance_records` — individual attendance entries
- `notifications` — user notifications
- `suspicious_activities` — fraud detection log
- `registration_slots` — registration time windows

---

## ⚙️ Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8080 | Server port |
| `jwt.secret` | (set in properties) | JWT signing key |
| `jwt.expiration` | 86400000 | Token TTL in ms (24h) |
| `aura.admin.secret` | AURA-ADMIN-2025 | Secret for admin registration |
