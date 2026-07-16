package com.smartdocs.service;

import com.smartdocs.dto.SecurityCenterResponse;
import com.smartdocs.entity.AuditLog;
import com.smartdocs.entity.LoginHistory;
import com.smartdocs.entity.User;
import com.smartdocs.exception.ResourceNotFoundException;
import com.smartdocs.repository.AuditLogRepository;
import com.smartdocs.repository.LoginHistoryRepository;
import com.smartdocs.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SecurityCenterServiceImpl implements SecurityCenterService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Override
    public SecurityCenterResponse getSecurityStatus(String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Last Login
        List<LoginHistory> logins = loginHistoryRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        LocalDateTime lastLogin = logins.isEmpty() ? null : logins.get(0).getCreatedAt();

        // Password Changed Date (check Audit Logs)
        LocalDateTime passwordChangedDate = user.getCreatedAt(); // Default
        List<AuditLog> audits = auditLogRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        for (AuditLog audit : audits) {
            if ("PASSWORD_RESET".equals(audit.getAction()) || "PASSWORD_CHANGE".equals(audit.getAction())) {
                passwordChangedDate = audit.getCreatedAt();
                break;
            }
        }

        // Failed login attempts in last 24 hours
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        long failedLogins = audits.stream()
                .filter(a -> "FAILED_LOGIN".equals(a.getAction()) && a.getCreatedAt().isAfter(oneDayAgo))
                .count();

        // Calculate Security Score
        int score = 0;
        if (user.isEmailVerified()) {
            score += 40; // Email verification adds 40 points
        }
        if (user.isActive()) {
            score += 30; // Active status adds 30 points
        }
        if (failedLogins == 0) {
            score += 30; // No failed attempts adds 30 points
        } else if (failedLogins == 1) {
            score += 15;
        }

        return SecurityCenterResponse.builder()
                .emailVerified(user.isEmailVerified())
                .lastLogin(lastLogin)
                .passwordChangedDate(passwordChangedDate)
                .failedLoginAttempts(failedLogins)
                .accountStatus(user.isActive() ? "ACTIVE" : "SUSPENDED")
                .jwtSessionExpiry(LocalDateTime.now().plusHours(24)) // Representing session length
                .securityScore(score)
                .build();
    }
}
