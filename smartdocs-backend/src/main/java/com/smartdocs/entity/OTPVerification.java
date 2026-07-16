package com.smartdocs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OTPVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "resend_attempts", nullable = false)
    @Builder.Default
    private int resendAttempts = 0;

    @Column(name = "last_resend_at")
    private LocalDateTime lastResendAt;

    @Column(nullable = false, length = 50)
    private String purpose; // EMAIL_VERIFICATION, PASSWORD_RESET
}
