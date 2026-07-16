package com.smartdocs.controller;

import com.smartdocs.dto.DocResponse;
import com.smartdocs.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.smartdocs.exception.BadRequestException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AIController {

    @Autowired
    private AIService aiService;

    @GetMapping("/insights/{documentId}")
    public ResponseEntity<DocResponse> getInsights(@PathVariable Long documentId, Authentication authentication) {
        String email = authentication.getName();
        DocResponse response = aiService.getOrCreateInsights(documentId, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<DocResponse>> smartSearch(@RequestParam String query, Authentication authentication) {
        String email = authentication.getName();
        List<DocResponse> response = aiService.smartSearch(query, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggest-category")
    public ResponseEntity<Map<String, String>> suggestCategory(
            @RequestParam String filename,
            @RequestParam(required = false) Set<String> tags,
            Authentication authentication) {
        String email = authentication.getName();
        String suggested = aiService.suggestCategory(filename, tags, email);
        Map<String, String> response = new HashMap<>();
        response.put("suggestedCategory", suggested);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/process/{documentId}")
    public ResponseEntity<Map<String, String>> processDocument(@PathVariable Long documentId) {
        aiService.processDocument(documentId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "AI processing completed successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat/{documentId}")
    public ResponseEntity<Map<String, String>> chatWithDocument(
            @PathVariable Long documentId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String email = authentication.getName();
        String question = body.get("question");
        if (question == null || question.trim().isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Question is required");
            return ResponseEntity.badRequest().body(error);
        }
        String answer = aiService.chatWithDocument(documentId, question, email);
        Map<String, String> response = new HashMap<>();
        response.put("answer", answer);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggest-name/{documentId}")
    public ResponseEntity<Map<String, String>> suggestName(@PathVariable Long documentId, Authentication authentication) {
        String email = authentication.getName();
        String suggestedName = aiService.suggestName(documentId, email);
        Map<String, String> response = new HashMap<>();
        response.put("suggestedName", suggestedName);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggest-folder/{documentId}")
    public ResponseEntity<Map<String, Object>> suggestFolder(@PathVariable Long documentId, Authentication authentication) {
        String email = authentication.getName();
        Map<String, Object> response = aiService.suggestFolder(documentId, email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/translate")
    public ResponseEntity<Map<String, String>> translateText(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        String targetLanguage = body.get("targetLanguage");
        String translated = aiService.translateText(text, targetLanguage);
        if (translated == null || translated.trim().isEmpty()) {
            throw new BadRequestException("Translation failed. The translation engine returned empty response.");
        }
        Map<String, String> response = new HashMap<>();
        response.put("translatedText", translated);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyze-enterprise/{documentId}")
    public ResponseEntity<Map<String, Object>> analyzeEnterpriseDoc(
            @PathVariable Long documentId,
            @RequestParam String mode,
            Authentication authentication) {
        String email = authentication.getName();
        Map<String, Object> analysis = aiService.analyzeEnterpriseDoc(documentId, mode, email);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/search-history")
    public ResponseEntity<Map<String, Object>> getSearchHistory(Authentication authentication) {
        String email = authentication.getName();
        Map<String, Object> history = aiService.getSearchHistory(email);
        return ResponseEntity.ok(history);
    }
}
