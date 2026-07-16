package com.smartdocs.controller;

import com.smartdocs.dto.*;
import com.smartdocs.service.AuthService;
import com.smartdocs.service.OTPVerificationService;
import com.smartdocs.service.MailService;
import com.smartdocs.repository.UserRepository;
import com.smartdocs.entity.User;
import com.smartdocs.exception.BadRequestException;
import com.smartdocs.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private OTPVerificationService otpService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MailService mailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        String message = authService.register(registerRequest);
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyOtpRequest verifyOtpRequest) {
        String message = authService.verifyEmail(verifyOtpRequest);
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        JwtResponse jwtResponse = authService.login(loginRequest, request);
        return ResponseEntity.ok(jwtResponse);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String message = authService.forgotPassword(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String message = authService.resetPassword(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestParam String email, @RequestParam String purpose) {
        otpService.generateAndSendOTP(email, purpose);
        Map<String, String> response = new HashMap<>();
        response.put("message", "A new OTP code has been sent successfully.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/activate/verify")
    public ResponseEntity<?> verifyActivationToken(@RequestParam String token) {
        User user = userRepository.findByActivationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or unrecognized activation token."));

        if (user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Activation link expired.");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put("role", user.getRole().name());
        response.put("workspaceName", user.getWorkspace() != null ? user.getWorkspace().getName() : "SmartDocs");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activateAccount(@Valid @RequestBody com.smartdocs.dto.ActivateAccountRequest request) {
        User user = userRepository.findByActivationToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or unrecognized activation token."));

        if (user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Activation link expired.");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(true);
        user.setActive(true);
        user.setActivationToken(null);
        user.setTokenExpiry(null);
        userRepository.save(user);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Account activated successfully. You can now log in.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activate/resend")
    public ResponseEntity<?> resendActivationLink(@RequestParam String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("No pending invitation found for this email."));

        if (user.isEmailVerified()) {
            throw new BadRequestException("This account is already activated. Please log in.");
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusHours(24);

        user.setActivationToken(token);
        user.setTokenExpiry(expiry);
        userRepository.save(user);

        String activationLink = getActivationLink(token);
        mailService.sendInvitationEmail(
                user.getEmail(),
                user.getName(),
                user.getWorkspace() != null ? user.getWorkspace().getName() : "SmartDocs",
                user.getRole().name(),
                activationLink
        );

        Map<String, String> response = new HashMap<>();
        response.put("message", "A new activation link has been sent to your email.");
        return ResponseEntity.ok(response);
    }

    private String getActivationLink(String token) {
        return frontendUrl + "/activate-account?token=" + token;
    }
}
