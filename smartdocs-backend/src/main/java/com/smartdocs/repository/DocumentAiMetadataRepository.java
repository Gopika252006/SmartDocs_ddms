package com.smartdocs.repository;

import com.smartdocs.entity.Document;
import com.smartdocs.entity.DocumentAiMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentAiMetadataRepository extends JpaRepository<DocumentAiMetadata, Long> {
    Optional<DocumentAiMetadata> findByDocument(Document document);
    void deleteByDocument(Document document);
}
