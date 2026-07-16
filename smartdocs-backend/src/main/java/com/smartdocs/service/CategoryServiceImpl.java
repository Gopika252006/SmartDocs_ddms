package com.smartdocs.service;

import com.smartdocs.dto.CategoryRequest;
import com.smartdocs.dto.CategoryResponse;
import com.smartdocs.entity.Category;
import com.smartdocs.entity.User;
import com.smartdocs.exception.BadRequestException;
import com.smartdocs.exception.ResourceNotFoundException;
import com.smartdocs.repository.CategoryRepository;
import com.smartdocs.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @EventListener(ContextRefreshedEvent.class)
    @Transactional
    public void initDefaultCategories() {
        if (categoryRepository.findByUserIsNull().isEmpty()) {
            String[] defaultCategories = {
                "Assignments", "Projects", "HR", "Finance",
                "Medical", "Bills", "Resume", "Research", "Certificates"
            };
            for (String name : defaultCategories) {
                Category category = Category.builder()
                        .name(name)
                        .user(null) // Null user indicates system default
                        .build();
                categoryRepository.save(category);
            }
        }
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String name = request.getName().trim();
        com.smartdocs.entity.Workspace workspace = user.getWorkspace();

        if (workspace != null) {
            boolean existsInWorkspace = categoryRepository.existsByNameIgnoreCaseAndWorkspace(name, workspace);
            if (existsInWorkspace) {
                throw new BadRequestException("Category '" + name + "' already exists in your workspace");
            }
        } else {
            boolean existsSystem = categoryRepository.existsByNameIgnoreCaseAndUserIsNull(name);
            boolean existsUser = categoryRepository.existsByNameIgnoreCaseAndUser(name, user);
            if (existsSystem || existsUser) {
                throw new BadRequestException("Category '" + name + "' already exists");
            }
        }

        Category category = Category.builder()
                .name(name)
                .user(user)
                .workspace(workspace)
                .build();

        categoryRepository.save(category);

        return mapToResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (category.getUser() == null) {
            throw new BadRequestException("Cannot edit system default categories");
        }

        if (category.getWorkspace() != null) {
            if (!category.getWorkspace().getId().equals(user.getWorkspace().getId())) {
                throw new BadRequestException("Access denied: You cannot edit this category");
            }
        } else {
            if (!category.getUser().getEmail().equals(userEmail)) {
                throw new BadRequestException("Access denied: You do not own this category");
            }
        }

        String name = request.getName().trim();
        category.setName(name);
        categoryRepository.save(category);

        return mapToResponse(category);
    }

    @Override
    public List<CategoryResponse> getCategoriesForUser(String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getWorkspace() != null) {
            return categoryRepository.findByWorkspace(user.getWorkspace())
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }

        return categoryRepository.findByUserOrUserIsNull(user)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteCategory(Long categoryId, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (category.getUser() == null) {
            throw new BadRequestException("Cannot delete system default categories");
        }

        if (category.getWorkspace() != null) {
            if (!category.getWorkspace().getId().equals(user.getWorkspace().getId())) {
                throw new BadRequestException("Access denied: You cannot delete this category");
            }
        } else {
            if (!category.getUser().getEmail().equals(userEmail)) {
                throw new BadRequestException("Access denied: You do not own this category");
            }
        }

        categoryRepository.delete(category);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .isDefault(category.getUser() == null)
                .build();
    }
}
