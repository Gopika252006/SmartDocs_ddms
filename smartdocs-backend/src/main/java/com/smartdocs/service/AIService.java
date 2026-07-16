package com.smartdocs.service;

import com.smartdocs.dto.DocResponse;

import java.util.List;
import java.util.Set;

public interface AIService {
    List<DocResponse> smartSearch(String query, String userEmail);
    String suggestCategory(String filename, Set<String> tags, String userEmail);
    void processDocument(Long documentId);
    void processDocumentEntity(com.smartdocs.entity.Document doc);
    String chatWithDocument(Long documentId, String question, String userEmail);

    String suggestName(Long documentId, String userEmail);
    java.util.Map<String, Object> suggestFolder(Long documentId, String userEmail);
    java.util.Map<String, Object> analyzeEnterpriseDoc(Long documentId, String mode, String userEmail);
    String translateText(String text, String targetLanguage);
    String generateWorkspaceInsights(long totalFiles, long totalUsers, long storageUsed, java.util.List<java.util.Map<String, Object>> categoryStats);
    java.util.Map<String, Object> getSearchHistory(String userEmail);
    DocResponse getOrCreateInsights(Long documentId, String userEmail);
    boolean testConnection();
    String getSelectedModel();
}
