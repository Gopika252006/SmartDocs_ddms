package com.smartdocs.service;

import com.smartdocs.dto.CategoryRequest;
import com.smartdocs.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {
    CategoryResponse createCategory(CategoryRequest request, String userEmail);
    CategoryResponse updateCategory(Long categoryId, CategoryRequest request, String userEmail);
    List<CategoryResponse> getCategoriesForUser(String userEmail);
    void deleteCategory(Long categoryId, String userEmail);
}
