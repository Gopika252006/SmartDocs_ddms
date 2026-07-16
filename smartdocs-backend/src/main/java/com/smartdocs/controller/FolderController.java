package com.smartdocs.controller;

import com.smartdocs.dto.FolderRequest;
import com.smartdocs.dto.FolderResponse;
import com.smartdocs.service.FolderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FolderController {

    @Autowired
    private FolderService folderService;

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(@Valid @RequestBody FolderRequest request, Authentication authentication) {
        String email = authentication.getName();
        FolderResponse response = folderService.createFolder(request, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> getRootFolders(Authentication authentication) {
        String email = authentication.getName();
        List<FolderResponse> response = folderService.getRootFolders(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<List<FolderResponse>> getAllFolders(Authentication authentication) {
        String email = authentication.getName();
        List<FolderResponse> response = folderService.getAllFolders(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/subfolders")
    public ResponseEntity<List<FolderResponse>> getSubfolders(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        List<FolderResponse> response = folderService.getSubfolders(id, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderResponse> getFolderDetails(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        FolderResponse response = folderService.getFolderDetails(id, email);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<FolderResponse> renameFolder(@PathVariable Long id, @RequestParam String name, Authentication authentication) {
        String email = authentication.getName();
        FolderResponse response = folderService.renameFolder(id, name, email);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFolder(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        folderService.softDeleteFolder(id, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Folder and all its contents deleted successfully");
        return ResponseEntity.ok(response);
    }
}
