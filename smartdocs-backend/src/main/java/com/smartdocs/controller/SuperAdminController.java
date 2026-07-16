package com.smartdocs.controller;

import com.smartdocs.entity.*;
import com.smartdocs.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/super")
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Transactional(readOnly = true)
public class SuperAdminController {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 1. GET ALL WORKSPACES
    @GetMapping("/workspaces")
    public ResponseEntity<List<Map<String, Object>>> getWorkspaces() {
        List<Workspace> workspaces = workspaceRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();

        for (Workspace ws : workspaces) {
            Map<String, Object> wsMap = new HashMap<>();
            wsMap.put("id", ws.getId());
            wsMap.put("name", ws.getName());
            wsMap.put("workspaceType", ws.getWorkspaceType());
            wsMap.put("active", ws.isActive());
            wsMap.put("createdAt", ws.getCreatedAt());
            wsMap.put("maxStorageLimit", ws.getMaxStorageLimit() != null ? ws.getMaxStorageLimit() : 
                ("PERSONAL".equalsIgnoreCase(ws.getWorkspaceType()) ? 100L * 1024 * 1024 : 50L * 1024 * 1024 * 1024));

            long userCount = userRepository.countByWorkspaceAndDeletedAtIsNull(ws);
            List<Document> docs = documentRepository.findByWorkspaceAndIsDeletedFalse(ws);
            long docCount = docs.size();
            long storageSize = docs.stream().mapToLong(Document::getFileSize).sum();

            // Find Workspace Admin (Owner)
            Optional<User> admin = userRepository.findAll().stream()
                    .filter(u -> u.getWorkspace() != null && u.getWorkspace().getId().equals(ws.getId()) && u.getRole() == Role.ADMIN)
                    .findFirst();

            wsMap.put("userCount", userCount);
            wsMap.put("docCount", docCount);
            wsMap.put("storageSize", storageSize);
            wsMap.put("adminName", admin.map(User::getName).orElse("No Admin Linked"));
            wsMap.put("adminEmail", admin.map(User::getEmail).orElse(""));

            response.add(wsMap);
        }
        return ResponseEntity.ok(response);
    }

    // 2. TOGGLE WORKSPACE STATUS (SUSPEND/ACTIVATE)
    @PostMapping("/workspaces/{id}/status")
    public ResponseEntity<?> toggleWorkspaceStatus(@PathVariable Long id, @RequestParam boolean active) {
        Workspace ws = workspaceRepository.findById(id).orElse(null);
        if (ws == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Workspace not found"));
        }
        ws.setActive(active);
        workspaceRepository.save(ws);
        return ResponseEntity.ok(Map.of("message", "Workspace status updated successfully to " + (active ? "ACTIVE" : "SUSPENDED")));
    }

    // 2.5 UPDATE WORKSPACE STORAGE QUOTA
    @PutMapping("/workspaces/{id}/quota")
    @Transactional
    public ResponseEntity<?> updateWorkspaceQuota(@PathVariable Long id, @RequestParam Long quotaBytes) {
        Workspace ws = workspaceRepository.findById(id).orElse(null);
        if (ws == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Workspace not found"));
        }
        if (quotaBytes == null || quotaBytes <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid storage quota size"));
        }
        ws.setMaxStorageLimit(quotaBytes);
        workspaceRepository.save(ws);
        return ResponseEntity.ok(Map.of("message", "Workspace storage quota updated successfully to " + quotaBytes + " bytes"));
    }

    // 3. RESET WORKSPACE ADMIN PASSWORD
    @PostMapping("/workspaces/{id}/reset-admin")
    public ResponseEntity<?> resetWorkspaceAdmin(@PathVariable Long id) {
        Workspace ws = workspaceRepository.findById(id).orElse(null);
        if (ws == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Workspace not found"));
        }

        Optional<User> adminOpt = userRepository.findAll().stream()
                .filter(u -> u.getWorkspace() != null && u.getWorkspace().getId().equals(ws.getId()) && u.getRole() == Role.ADMIN)
                .findFirst();

        if (adminOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Admin user not found for this workspace"));
        }

        User admin = adminOpt.get();
        admin.setPassword(passwordEncoder.encode("SecurePass123!"));
        userRepository.save(admin);

        return ResponseEntity.ok(Map.of("message", "Workspace admin password has been reset successfully to 'SecurePass123!'"));
    }

    // 4. GET PLATFORM USERS
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers(@RequestParam(required = false) Long workspaceId) {
        List<User> users = userRepository.findAll();
        if (workspaceId != null) {
            users = users.stream()
                    .filter(u -> u.getWorkspace() != null && u.getWorkspace().getId().equals(workspaceId))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> response = users.stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getName());
            map.put("email", u.getEmail());
            map.put("role", u.getRole());
            map.put("active", u.isActive());
            map.put("workspaceName", u.getWorkspace() != null ? u.getWorkspace().getName() : "Platform Owner");
            map.put("workspaceType", u.getWorkspace() != null ? u.getWorkspace().getWorkspaceType() : "SYSTEM");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // 5. GET PLATFORM DOCUMENTS
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> getAllDocuments() {
        List<Document> docs = documentRepository.findAll();
        List<Map<String, Object>> response = docs.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("name", d.getName());
            map.put("fileType", d.getFileType());
            map.put("fileSize", d.getFileSize());
            map.put("uploaderName", d.getUser() != null ? d.getUser().getName() : "Unknown");
            map.put("workspaceName", d.getWorkspace() != null ? d.getWorkspace().getName() : "Global");
            map.put("createdAt", d.getCreatedAt());
            map.put("duplicateStatus", d.getDuplicateStatus());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // 6. GET PLATFORM AUDIT LOGS
    @GetMapping("/audit-logs")
    public ResponseEntity<List<Map<String, Object>>> getAllAuditLogs(@RequestParam(required = false) Long workspaceId) {
        List<AuditLog> logs = auditLogRepository.findAll();
        if (workspaceId != null) {
            logs = logs.stream()
                    .filter(l -> (l.getUser() != null && l.getUser().getWorkspace() != null && l.getUser().getWorkspace().getId().equals(workspaceId)) ||
                                 (l.getDocument() != null && l.getDocument().getWorkspace() != null && l.getDocument().getWorkspace().getId().equals(workspaceId)))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> response = logs.stream().map(l -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", l.getId());
            map.put("action", l.getAction());
            map.put("ipAddress", l.getIpAddress());
            map.put("browser", l.getBrowser());
            map.put("os", l.getOs());
            map.put("device", l.getDevice());
            map.put("createdAt", l.getCreatedAt());

            String userName = "System";
            String userEmail = "";
            try {
                if (l.getUser() != null) {
                    userName = l.getUser().getName();
                    userEmail = l.getUser().getEmail();
                }
            } catch (Exception e) {
                // Ignore missing user proxy
            }
            map.put("userName", userName);
            map.put("userEmail", userEmail);

            String documentName = "N/A";
            try {
                if (l.getDocument() != null) {
                    documentName = l.getDocument().getName();
                }
            } catch (Exception e) {
                // Ignore missing document proxy
            }
            map.put("documentName", documentName);
            
            String workspaceName = "System";
            try {
                if (l.getUser() != null && l.getUser().getWorkspace() != null) {
                    workspaceName = l.getUser().getWorkspace().getName();
                } else if (l.getDocument() != null && l.getDocument().getWorkspace() != null) {
                    workspaceName = l.getDocument().getWorkspace().getName();
                }
            } catch (Exception e) {
                // Ignore missing workspace proxy
            }
            map.put("workspaceName", workspaceName);
            
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // 7. GET PLATFORM ANALYTICS
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getPlatformAnalytics() {
        List<Workspace> workspaces = workspaceRepository.findAll();
        List<User> users = userRepository.findAll();
        List<Document> documents = documentRepository.findAll();

        long totalWorkspaces = workspaces.size();
        long totalUsers = users.size();
        long totalDocuments = documents.size();
        long totalStorage = documents.stream().mapToLong(Document::getFileSize).sum();

        // Workspace type breakdown
        Map<String, Long> workspaceTypeBreakdown = workspaces.stream()
                .collect(Collectors.groupingBy(Workspace::getWorkspaceType, Collectors.counting()));

        // Storage size breakdown by workspace
        Map<String, Long> storageBreakdown = new HashMap<>();
        for (Workspace ws : workspaces) {
            long size = documents.stream()
                    .filter(d -> d.getWorkspace() != null && d.getWorkspace().getId().equals(ws.getId()))
                    .mapToLong(Document::getFileSize)
                    .sum();
            storageBreakdown.put(ws.getName(), size);
        }

        // AI counts
        long aiSummariesCount = documents.stream()
                .filter(d -> d.getAiSummary() != null && !d.getAiSummary().isEmpty())
                .count();

        // Duplicate counts
        long duplicateCount = documents.stream()
                .filter(d -> d.getDuplicateStatus() != null && d.getDuplicateStatus().equalsIgnoreCase("DUPLICATE"))
                .count();

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalWorkspaces", totalWorkspaces);
        analytics.put("totalUsers", totalUsers);
        analytics.put("totalDocuments", totalDocuments);
        analytics.put("totalStorage", totalStorage);
        analytics.put("workspaceTypeBreakdown", workspaceTypeBreakdown);
        analytics.put("storageBreakdown", storageBreakdown);
        analytics.put("aiSummariesCount", aiSummariesCount);
        analytics.put("duplicateCount", duplicateCount);

        return ResponseEntity.ok(analytics);
    }
}
