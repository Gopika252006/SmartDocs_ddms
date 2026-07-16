package com.smartdocs.controller;

import com.smartdocs.dto.CategoryRequest;
import com.smartdocs.dto.CategoryResponse;
import com.smartdocs.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request, Authentication authentication) {
        String email = authentication.getName();
        CategoryResponse response = categoryService.createCategory(request, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories(Authentication authentication) {
        String email = authentication.getName();
        List<CategoryResponse> response = categoryService.getCategoriesForUser(email);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request, Authentication authentication) {
        String email = authentication.getName();
        CategoryResponse response = categoryService.updateCategory(id, request, email);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        categoryService.deleteCategory(id, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Custom category deleted successfully");
        return ResponseEntity.ok(response);
    }
}
