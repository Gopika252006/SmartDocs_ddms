package com.smartdocs.controller;

import com.smartdocs.entity.AuditLog;
import com.smartdocs.entity.Document;
import com.smartdocs.entity.User;
import com.smartdocs.entity.Workspace;
import com.smartdocs.exception.ResourceNotFoundException;
import com.smartdocs.repository.AuditLogRepository;
import com.smartdocs.repository.DocumentRepository;
import com.smartdocs.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*", maxAge = 3600)
@Transactional
public class ReportController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private com.smartdocs.service.AIService aiService;

    @Value("${app.jwt.expirationMs}")
    private int dummySessionTime; // Config check

    private static final long MAX_STORAGE_LIMIT = 50L * 1024 * 1024 * 1024; // 50 GB system capacity

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummaryStats(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Map<String, Object> stats = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        boolean isAdmin = user.getRole() == com.smartdocs.entity.Role.ADMIN || user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN;

        Workspace workspace = user.getWorkspace();
        long totalUsers;
        long totalFiles;
        Long storageUsedBytes;
        List<Object[]> monthlyData;
        List<Object[]> categoryData;

        if (user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            totalUsers = userRepository.count();
            totalFiles = documentRepository.findAll().stream().filter(d -> !d.isDeleted()).count();
            storageUsedBytes = documentRepository.getTotalStorageUsed();
            monthlyData = documentRepository.getMonthlyUploadStats();
            categoryData = documentRepository.getCategoryDistribution();
        } else if (workspace != null) {
            totalUsers = isAdmin ? userRepository.countByWorkspaceAndDeletedAtIsNull(workspace) : 1;
            if (isAdmin) {
                totalFiles = documentRepository.findByWorkspaceAndIsDeletedFalse(workspace).size();
                storageUsedBytes = documentRepository.getStorageUsedByWorkspace(workspace);
            } else {
                totalFiles = documentRepository.findByUserAndIsDeletedFalse(user).size();
                storageUsedBytes = documentRepository.getStorageUsedByUser(user);
            }
            monthlyData = isAdmin ? 
                documentRepository.getMonthlyUploadStatsForWorkspace(workspace) : 
                documentRepository.getMonthlyUploadStatsForUser(user);
            categoryData = isAdmin ? 
                documentRepository.getCategoryDistributionForWorkspace(workspace) : 
                documentRepository.getCategoryDistributionForUser(user);
        } else {
            totalUsers = 1;
            totalFiles = documentRepository.findByUserAndIsDeletedFalse(user).size();
            storageUsedBytes = documentRepository.getStorageUsedByUser(user);
            monthlyData = documentRepository.getMonthlyUploadStatsForUser(user);
            categoryData = documentRepository.getCategoryDistributionForUser(user);
        }

        if (storageUsedBytes == null) {
            storageUsedBytes = 0L;
        }

        long limitBytes;
        if (user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            limitBytes = MAX_STORAGE_LIMIT;
        } else if (workspace != null) {
            limitBytes = workspace.getMaxStorageLimit() != null ? workspace.getMaxStorageLimit() : 
                ("PERSONAL".equalsIgnoreCase(workspace.getWorkspaceType()) ? 100L * 1024 * 1024 : 50L * 1024 * 1024 * 1024);
        } else {
            limitBytes = 100L * 1024 * 1024;
        }
        long remainingStorageBytes = Math.max(0L, limitBytes - storageUsedBytes);

        stats.put("totalUsers", totalUsers);
        stats.put("totalFiles", totalFiles);
        stats.put("storageUsed", storageUsedBytes);
        stats.put("remainingStorage", remainingStorageBytes);
        stats.put("maxStorageLimit", limitBytes);
        stats.put("generatedAt", LocalDateTime.now().format(formatter));
        stats.put("role", user.getRole().name());

        List<Map<String, Object>> formattedMonthly = new ArrayList<>();
        for (Object[] row : monthlyData) {
            Map<String, Object> map = new HashMap<>();
            map.put("month", row[0]);
            map.put("count", row[1]);
            formattedMonthly.add(map);
        }

        List<Map<String, Object>> formattedCategory = new ArrayList<>();
        for (Object[] row : categoryData) {
            Map<String, Object> map = new HashMap<>();
            map.put("category", row[0]);
            map.put("count", row[1]);
            formattedCategory.add(map);
        }

        stats.put("monthlyStats", formattedMonthly);
        stats.put("categoryStats", formattedCategory);

        // Fetch duplicates and compute savings
        List<com.smartdocs.entity.Document> docsList;
        if (user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            docsList = documentRepository.findAll().stream().filter(d -> !d.isDeleted()).collect(Collectors.toList());
        } else if (workspace != null) {
            docsList = documentRepository.findByWorkspaceAndIsDeletedFalse(workspace);
        } else {
            docsList = documentRepository.findByUserAndIsDeletedFalse(user);
        }

        long duplicateCount = docsList.stream()
                .filter(d -> d.getDuplicateStatus() != null && d.getDuplicateStatus().equalsIgnoreCase("POTENTIAL_DUPLICATE"))
                .count();

        long potentialSavingsBytes = docsList.stream()
                .filter(d -> d.getDuplicateStatus() != null && d.getDuplicateStatus().equalsIgnoreCase("POTENTIAL_DUPLICATE"))
                .mapToLong(com.smartdocs.entity.Document::getFileSize)
                .sum();

        // Fetch search queries and top searched keywords
        List<com.smartdocs.entity.AuditLog> searchLogs;
        if (user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            searchLogs = auditLogRepository.findAll().stream()
                    .filter(l -> l.getAction().startsWith("SEARCH: "))
                    .collect(Collectors.toList());
        } else if (workspace != null) {
            searchLogs = auditLogRepository.findAll().stream()
                    .filter(l -> l.getAction().startsWith("SEARCH: "))
                    .filter(l -> l.getUser() != null && l.getUser().getWorkspace() != null && l.getUser().getWorkspace().getId().equals(workspace.getId()))
                    .collect(Collectors.toList());
        } else {
            searchLogs = auditLogRepository.findAll().stream()
                    .filter(l -> l.getAction().startsWith("SEARCH: "))
                    .filter(l -> l.getUser() != null && l.getUser().getId().equals(user.getId()))
                    .collect(Collectors.toList());
        }

        Map<String, Long> queryFreq = searchLogs.stream()
                .map(l -> l.getAction().substring(8).trim().toLowerCase())
                .collect(Collectors.groupingBy(q -> q, Collectors.counting()));

        String topSearchKeyword = queryFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("No searches yet");

        // Calculate health score (weighted)
        long uncategorizedCount = docsList.stream().filter(d -> d.getCategory() == null).count();
        long missingMetadataCount = docsList.stream().filter(d -> d.getAiSummary() == null || d.getAiSummary().trim().isEmpty()).count();
        long totalDocCount = docsList.size();

        int healthScore = -1;
        if (totalDocCount > 0) {
            double healthDeduction = 0.0;
            healthDeduction += ((double) duplicateCount / totalDocCount) * 15.0;
            healthDeduction += ((double) uncategorizedCount / totalDocCount) * 15.0;
            healthDeduction += ((double) missingMetadataCount / totalDocCount) * 10.0;
            double storageUtilPercent = ((double) storageUsedBytes / limitBytes) * 100.0;
            healthDeduction += (storageUtilPercent * 0.1);
            healthScore = (int) Math.max(50.0, 100.0 - healthDeduction);
        }

        // Generate AI Workspace Insights
        String insights = aiService.generateWorkspaceInsights(totalFiles, totalUsers, storageUsedBytes, formattedCategory);

        stats.put("duplicateCount", duplicateCount);
        stats.put("potentialSavings", potentialSavingsBytes);
        stats.put("topSearchKeyword", topSearchKeyword);
        stats.put("healthScore", healthScore);
        stats.put("aiInsights", insights);

        return ResponseEntity.ok(stats);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @GetMapping("/export/users")
    public ResponseEntity<byte[]> exportUsersReport(Authentication authentication) {
        String currentEmail = authentication.getName();
        User admin = userRepository.findByEmailAndDeletedAtIsNull(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        Workspace workspace = admin.getWorkspace();
        List<User> users;
        if (admin.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            users = userRepository.findAllByDeletedAtIsNull();
        } else if (workspace != null) {
            users = userRepository.findAllByWorkspaceAndDeletedAtIsNull(workspace);
        } else {
            users = List.of(admin);
        }

        StringBuilder csv = new StringBuilder();
        csv.append("S.No,Name,Email,Role,Email Verified,ActiveStatus,RegisteredDate\n");

        int sNo = 1;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (User u : users) {
            csv.append(sNo++).append(",")
                    .append(sanitizeCsv(u.getName())).append(",")
                    .append(sanitizeCsv(u.getEmail())).append(",")
                    .append(u.getRole() != null ? u.getRole().name() : "").append(",")
                    .append(u.isEmailVerified()).append(",")
                    .append(u.isActive()).append(",")
                    .append(u.getCreatedAt() != null ? u.getCreatedAt().format(formatter) : "").append("\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @GetMapping("/export/documents")
    public ResponseEntity<byte[]> exportDocumentsReport(Authentication authentication) {
        String currentEmail = authentication.getName();
        User admin = userRepository.findByEmailAndDeletedAtIsNull(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        Workspace workspace = admin.getWorkspace();
        List<Document> documents;
        if (admin.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            documents = documentRepository.findAll().stream()
                    .filter(d -> !d.isDeleted())
                    .collect(Collectors.toList());
        } else if (workspace != null) {
            documents = documentRepository.findByWorkspaceAndIsDeletedFalse(workspace);
        } else {
            documents = documentRepository.findByUserAndIsDeletedFalse(admin);
        }

        StringBuilder csv = new StringBuilder();
        csv.append("S.No,Name,File Type,Size (Bytes),Folder,Category,Owner,UploadedDate,Is Deleted\n");

        int sNo = 1;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Document d : documents) {
            String ownerEmail = "Unknown";
            try {
                if (d.getUser() != null) {
                    ownerEmail = d.getUser().getEmail();
                }
            } catch (Exception e) {
                ownerEmail = "Deleted User";
            }

            csv.append(sNo++).append(",")
                    .append(sanitizeCsv(d.getName())).append(",")
                    .append(sanitizeCsv(d.getFileType())).append(",")
                    .append(d.getFileSize()).append(",")
                    .append(d.getFolder() != null ? sanitizeCsv(d.getFolder().getName()) : "Root").append(",")
                    .append(d.getCategory() != null ? sanitizeCsv(d.getCategory().getName()) : "None").append(",")
                    .append(sanitizeCsv(ownerEmail)).append(",")
                    .append(d.getCreatedAt() != null ? d.getCreatedAt().format(formatter) : "").append(",")
                    .append(d.isDeleted()).append("\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"documents_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @GetMapping("/export/activity")
    public ResponseEntity<byte[]> exportActivityReport(Authentication authentication) {
        String currentEmail = authentication.getName();
        User admin = userRepository.findByEmailAndDeletedAtIsNull(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        Workspace workspace = admin.getWorkspace();
        List<AuditLog> logs;
        if (admin.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            logs = auditLogRepository.searchAuditLogs(null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        } else if (workspace != null) {
            logs = auditLogRepository.searchAuditLogsInWorkspace(workspace, null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        } else {
            logs = auditLogRepository.searchAuditLogsForUser(admin, null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("S.No,User Email,Action,OS,Browser,Device,IP Address,Timestamp\n");

        int sNo = 1;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (AuditLog l : logs) {
            String userEmail = "System";
            try {
                if (l.getUser() != null) {
                    userEmail = l.getUser().getEmail();
                }
            } catch (Exception e) {
                userEmail = "Deleted User";
            }

            csv.append(sNo++).append(",")
                    .append(sanitizeCsv(userEmail)).append(",")
                    .append(sanitizeCsv(l.getAction())).append(",")
                    .append(sanitizeCsv(l.getOs())).append(",")
                    .append(sanitizeCsv(l.getBrowser())).append(",")
                    .append(sanitizeCsv(l.getDevice())).append(",")
                    .append(sanitizeCsv(l.getIpAddress())).append(",")
                    .append(l.getCreatedAt() != null ? l.getCreatedAt().format(formatter) : "").append("\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @GetMapping("/export/storage")
    public ResponseEntity<byte[]> exportStorageReport(Authentication authentication) {
        String currentEmail = authentication.getName();
        User admin = userRepository.findByEmailAndDeletedAtIsNull(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        Workspace workspace = admin.getWorkspace();
        List<Object[]> consumers;
        if (admin.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            consumers = documentRepository.getTopStorageConsumers(org.springframework.data.domain.PageRequest.of(0, 100));
        } else if (workspace != null) {
            consumers = documentRepository.getTopStorageConsumersForWorkspace(workspace, org.springframework.data.domain.PageRequest.of(0, 100));
        } else {
            consumers = documentRepository.getTopStorageConsumersForUser(admin, org.springframework.data.domain.PageRequest.of(0, 100));
        }

        StringBuilder csv = new StringBuilder();
        csv.append("S.No,Uploader Email,Storage Used (Bytes),Storage Used (MB)\n");

        int sNo = 1;
        for (Object[] row : consumers) {
            String email = row[0] != null ? (String) row[0] : "Unknown";
            Long bytesUsed = row[1] != null ? (Long) row[1] : 0L;
            double mbUsed = bytesUsed / (1024.0 * 1024.0);
            csv.append(sNo++).append(",")
                    .append(sanitizeCsv(email)).append(",")
                    .append(bytesUsed).append(",")
                    .append(String.format("%.2f", mbUsed)).append("\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"storage_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    private String sanitizeCsv(String val) {
        if (val == null) return "";
        String clean = val.replace("\"", "\"\"");
        if (clean.contains(",") || clean.contains("\n") || clean.contains("\"")) {
            return "\"" + clean + "\"";
        }
        return clean;
    }
}
