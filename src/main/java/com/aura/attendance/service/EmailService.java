package com.aura.attendance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * EmailService — sends absence alert emails to parent addresses.
 *
 * Safe to run without SMTP configured:
 * - If aura.mail.enabled=false (default), emails are only logged, never sent.
 * - JavaMailSender is Optional so the app starts even without SMTP env vars.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // Optional — avoids startup crash if spring.mail is not configured
    private final Optional<JavaMailSender> mailSender;

    @Value("${aura.mail.from:noreply-aura@college.edu}")
    private String fromAddress;

    @Value("${aura.mail.enabled:false}")
    private boolean mailEnabled;

    public EmailService(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
        if (mailSender.isEmpty()) {
            log.warn("[EmailService] JavaMailSender not available — email sending is disabled. Set SMTP env vars to enable.");
        }
    }

    /**
     * Send an absence notification to a parent/guardian.
     *
     * @param toEmail      parent's email address
     * @param studentName  student's full name
     * @param rollNumber   student's roll number
     * @param subjectName  subject name
     * @param teacherName  teacher who conducted the session
     * @param date         session date (formatted string)
     */
    public void sendAbsenceAlert(String toEmail, String studentName, String rollNumber,
                                  String subjectName, String teacherName, String date) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("[EmailService] No parent email for student {}. Skipping.", rollNumber);
            return;
        }

        String subject = "AURA Attendance Alert — " + studentName + " was absent";
        String body = """
                Dear Parent / Guardian,

                This is an automated attendance notification from AURA — the Automated Unified Real-Time Attendance System.

                Student Details:
                  Name        : %s
                  Roll Number : %s
                  Subject     : %s
                  Teacher     : %s
                  Date        : %s
                  Status      : ABSENT

                Your ward was marked ABSENT for the above class. If this is incorrect, please contact the concerned teacher.

                This is a system-generated email. Please do not reply to this address.

                Regards,
                AURA Attendance System
                """.formatted(studentName, rollNumber, subjectName, teacherName, date);

        if (!mailEnabled) {
            log.info("[EmailService] Mail disabled — would have sent to {}: Subject='{}'", toEmail, subject);
            log.debug("[EmailService] Body:\n{}", body);
            return;
        }

        if (mailSender.isEmpty()) {
            log.error("[EmailService] Mail enabled but JavaMailSender not available. Check SMTP environment variables.");
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(toEmail);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.get().send(msg);
            log.info("[EmailService] Absence alert sent to {} for student {}", toEmail, rollNumber);
        } catch (Exception e) {
            log.error("[EmailService] Failed to send email to {} for student {}: {}", toEmail, rollNumber, e.getMessage());
            // Don't rethrow — email failure should NOT break the API response
        }
    }
}
