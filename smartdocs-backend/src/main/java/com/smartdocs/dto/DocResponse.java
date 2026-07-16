package com.smartdocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocResponse {
    private Long id;
    private String name;
    private String fileType;
    private Long fileSize;
    private Long folderId;
    private String folderName;
    private Long categoryId;
    private String categoryName;
    private Long userId;
    private String uploaderName;
    private String uploaderEmail;
    private Set<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isDeleted;
    private String aiSummary;
    private String aiHighlights;
    private String aiKeywords;
    private String aiMetadata;
    private String aiCategory;
    private String duplicateStatus;
    private Double duplicateScore;
    private String relatedDocumentIds;
    private boolean isSensitive;
    private boolean piiDetected;
    private String confidentialityClass;
    private String sensitiveDetails;
}
