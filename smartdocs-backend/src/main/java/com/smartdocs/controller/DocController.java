package com.smartdocs.controller;

import com.smartdocs.dto.DocResponse;
import com.smartdocs.dto.DocVersionResponse;
import com.smartdocs.dto.ShareRequest;
import com.smartdocs.dto.ShareResponse;
import com.smartdocs.service.DocService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/docs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DocController {

    @Autowired
    private DocService docService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "tags", required = false) Set<String> tags,
            Authentication authentication) {
        String email = authentication.getName();
        DocResponse response = docService.uploadDocument(file, name, folderId, categoryId, tags, email);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{id}/new-version", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocResponse> uploadNewVersion(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        String email = authentication.getName();
        DocResponse response = docService.uploadNewVersion(id, file, email);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<DocResponse> renameDocument(
            @PathVariable Long id,
            @RequestParam("name") String name,
            Authentication authentication) {
        String email = authentication.getName();
        DocResponse response = docService.renameDocument(id, name, email);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/soft")
    public ResponseEntity<?> softDeleteDocument(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        docService.softDeleteDocument(id, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Document moved to Trash successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restoreDocument(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        docService.restoreDocument(id, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Document restored from Trash successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<?> permanentDeleteDocument(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        docService.permanentDeleteDocument(id, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Document and all its version histories deleted permanently");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<DocResponse>> getDocumentsInFolder(@PathVariable Long folderId, Authentication authentication) {
        String email = authentication.getName();
        List<DocResponse> response = docService.getDocumentsInFolder(folderId, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/root")
    public ResponseEntity<List<DocResponse>> getRootDocuments(Authentication authentication) {
        String email = authentication.getName();
        List<DocResponse> response = docService.getRootDocuments(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/trash")
    public ResponseEntity<List<DocResponse>> getTrashedDocuments(Authentication authentication) {
        String email = authentication.getName();
        List<DocResponse> response = docService.getTrashedDocuments(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<DocVersionResponse>> getDocumentVersions(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        List<DocVersionResponse> response = docService.getDocumentVersions(id, email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<ShareResponse> shareDocument(
            @PathVariable Long id,
            @Valid @RequestBody ShareRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        ShareResponse response = docService.shareDocument(id, request, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/shared/with-me")
    public ResponseEntity<List<ShareResponse>> getSharedWithMe(Authentication authentication) {
        String email = authentication.getName();
        List<ShareResponse> response = docService.getSharedWithMe(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/shared/by-me")
    public ResponseEntity<List<ShareResponse>> getSharedByMe(Authentication authentication) {
        String email = authentication.getName();
        List<ShareResponse> response = docService.getSharedByMe(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long id,
            @RequestParam(value = "version", required = false) Integer version,
            Authentication authentication) {
        String email = authentication.getName();
        Resource resource = docService.loadFileAsResource(id, version, email);

        String contentType = null;
        try {
            contentType = URLConnection.guessContentTypeFromName(resource.getFile().getName());
        } catch (IOException e) {
            contentType = "application/octet-stream";
        }
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}/versions/{versionNumber}")
    public ResponseEntity<?> deleteDocumentVersion(
            @PathVariable Long id,
            @PathVariable Integer versionNumber,
            Authentication authentication) {
        String email = authentication.getName();
        docService.deleteDocumentVersion(id, versionNumber, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Document version v" + versionNumber + " deleted successfully");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<DocResponse> moveDocument(
            @PathVariable Long id,
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {
        String email = authentication.getName();
        DocResponse response = docService.moveDocument(id, folderId, email);
        return ResponseEntity.ok(response);
    }
}
