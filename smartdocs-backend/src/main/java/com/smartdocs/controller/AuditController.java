package com.smartdocs.controller;

import com.smartdocs.dto.AuditLogResponse;
import com.smartdocs.entity.AuditLog;
import com.smartdocs.entity.User;
import com.smartdocs.entity.Workspace;
import com.smartdocs.repository.AuditLogRepository;
import com.smartdocs.repository.UserRepository;
import com.smartdocs.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('EMPLOYEE')")
@Transactional(readOnly = true)
public class AuditController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String browser,
            @RequestParam(required = false) String os,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Pageable pageable = PageRequest.of(page, size);

        // Map nulls and empty parameters
        String filterEmail = (email == null || email.trim().isEmpty()) ? null : email.trim();
        String filterAction = (action == null || action.trim().isEmpty()) ? null : action.trim();
        String filterBrowser = (browser == null || browser.trim().isEmpty()) ? null : browser.trim();
        String filterOs = (os == null || os.trim().isEmpty()) ? null : os.trim();
        String filterIp = (ipAddress == null || ipAddress.trim().isEmpty()) ? null : ipAddress.trim();

        String currentEmail = authentication.getName();
        User admin = userRepository.findByEmailAndDeletedAtIsNull(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Workspace workspace = admin.getWorkspace();
        Page<AuditLog> logs;

        // If regular employee, strictly filter by their own email only!
        if (admin.getRole() == com.smartdocs.entity.Role.EMPLOYEE) {
            filterEmail = currentEmail;
        }

        if (workspace != null) {
            logs = auditLogRepository.searchAuditLogsInWorkspace(
                    workspace, filterEmail, filterAction, filterBrowser, filterOs, filterIp, pageable
            );
        } else {
            if (admin.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
                logs = auditLogRepository.searchAuditLogs(
                        filterEmail, filterAction, filterBrowser, filterOs, filterIp, pageable
                );
            } else {
                logs = auditLogRepository.searchAuditLogsForUser(
                        admin, filterEmail, filterAction, filterBrowser, filterOs, filterIp, pageable
                );
            }
        }

        Page<AuditLogResponse> response = logs.map(log -> {
            Long userId = null;
            String userName = "System";
            String userEmail = "system@smartdocs.com";
            try {
                if (log.getUser() != null) {
                    userId = log.getUser().getId();
                    userName = log.getUser().getName();
                    userEmail = log.getUser().getEmail();
                }
            } catch (Exception e) {
                // Ignore missing user proxy
            }

            Long documentId = null;
            String documentName = null;
            try {
                if (log.getDocument() != null) {
                    documentId = log.getDocument().getId();
                    documentName = log.getDocument().getName();
                }
            } catch (Exception e) {
                // Ignore missing document proxy
            }

            return AuditLogResponse.builder()
                    .id(log.getId())
                    .userId(userId)
                    .userName(userName)
                    .userEmail(userEmail)
                    .action(log.getAction())
                    .documentId(documentId)
                    .documentName(documentName)
                    .browser(log.getBrowser())
                    .os(log.getOs())
                    .device(log.getDevice())
                    .ipAddress(log.getIpAddress())
                    .createdAt(log.getCreatedAt())
                    .build();
        });

        return ResponseEntity.ok(response);
    }
}
