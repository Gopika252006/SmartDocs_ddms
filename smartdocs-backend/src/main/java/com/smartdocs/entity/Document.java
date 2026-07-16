package com.smartdocs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType; // PDF, DOCX, PPT, PPTX, PNG, JPG, ZIP

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Lob
    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_highlights", columnDefinition = "TEXT")
    private String aiHighlights;

    @Column(name = "ai_keywords", columnDefinition = "TEXT")
    private String aiKeywords;

    @Column(name = "ai_metadata", columnDefinition = "TEXT")
    private String aiMetadata;

    @Column(name = "ai_category", length = 100)
    private String aiCategory;

    @Column(name = "duplicate_status", length = 50)
    private String duplicateStatus;

    @Column(name = "duplicate_score")
    private Double duplicateScore;

    @Column(name = "related_document_ids", length = 255)
    private String relatedDocumentIds;

    @Column(name = "is_sensitive", nullable = false)
    @Builder.Default
    private boolean isSensitive = false;

    @Column(name = "pii_detected", nullable = false)
    @Builder.Default
    private boolean piiDetected = false;

    @Column(name = "confidentiality_class", length = 50)
    @Builder.Default
    private String confidentialityClass = "Public";

    @Column(name = "sensitive_details", columnDefinition = "TEXT")
    private String sensitiveDetails;

    @Lob
    @Column(name = "search_index", columnDefinition = "LONGTEXT")
    private String searchIndex;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "document_tags", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "tag", length = 50)
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
