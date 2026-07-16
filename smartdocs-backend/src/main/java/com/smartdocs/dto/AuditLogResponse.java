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
public class AuditLogResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String action;
    private Long documentId;
    private String documentName;
    private String browser;
    private String os;
    private String device;
    private String ipAddress;
    private LocalDateTime createdAt;
}
