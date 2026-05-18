package com.aura.attendance.service;

import com.aura.attendance.dto.*;
import com.aura.attendance.entity.User;
import com.aura.attendance.repository.UserRepository;
import com.aura.attendance.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authManager;

    @Value("${aura.admin.secret}")
    private String adminSecret;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, AuthenticationManager authManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authManager = authManager;
    }

    // ---- LOGIN ----
    public AuthResponse login(LoginRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank())
            throw new RuntimeException("Email is required");
        if (req.getPassword() == null || req.getPassword().isBlank())
            throw new RuntimeException("Password is required");

        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail().trim(), req.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new RuntimeException("Invalid email or password");
        } catch (Exception e) {
            throw new RuntimeException("Login failed: " + e.getMessage());
        }

        User user = userRepository.findByEmail(req.getEmail().trim())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtUtil.generateToken(user.getEmail());
        return buildResponse(user, token);
    }

    // ---- REGISTER ADMIN ----
    public AuthResponse registerAdmin(RegisterRequest req) {
        // Validate required fields
        if (req.getFullName() == null || req.getFullName().isBlank())
            throw new RuntimeException("Full name is required");
        if (req.getEmail() == null || req.getEmail().isBlank())
            throw new RuntimeException("Email is required");
        if (req.getPassword() == null || req.getPassword().length() < 6)
            throw new RuntimeException("Password must be at least 6 characters");

        // Verify admin secret
        if (req.getAdminSecret() == null || req.getAdminSecret().isBlank())
            throw new RuntimeException("Admin secret key is required");
        if (!adminSecret.equals(req.getAdminSecret().trim()))
            throw new RuntimeException("Invalid admin secret key");

        // Check email not taken
        if (userRepository.existsByEmail(req.getEmail().trim()))
            throw new RuntimeException("An account with this email already exists");

        User user = User.builder()
                .fullName(req.getFullName().trim())
                .email(req.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(User.AppRole.admin)
                .build();

        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail());
        return buildResponse(user, token);
    }

    private AuthResponse buildResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .department(user.getDepartment() != null ? user.getDepartment().name() : null)
                .year(user.getYear())
                .section(user.getSection())
                .rollNumber(user.getRollNumber())
                .facultyId(user.getFacultyId())
                .parentEmail(user.getParentEmail())
                .phone(user.getPhone())
                .deviceFingerprint(user.getDeviceFingerprint())
                .build();
    }
}
