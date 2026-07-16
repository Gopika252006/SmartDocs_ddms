package com.smartdocs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_ai_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAiMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private Document document;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "folder_recommendation", length = 255)
    private String folderRecommendation;

    @Column(name = "important_points", columnDefinition = "TEXT")
    private String importantPoints;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "model_used", length = 100)
    private String modelUsed;
}
