package com.smartdocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SecurityCenterResponse {
    private boolean emailVerified;
    private LocalDateTime lastLogin;
    private LocalDateTime passwordChangedDate;
    private long failedLoginAttempts;
    private String accountStatus;
    private LocalDateTime jwtSessionExpiry;
    private int securityScore; // 0 to 100
}
