package com.smartdocs.repository;

import com.smartdocs.entity.Category;
import com.smartdocs.entity.User;
import com.smartdocs.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByWorkspace(Workspace workspace);

    boolean existsByNameIgnoreCaseAndWorkspace(String name, Workspace workspace);

    Optional<Category> findByNameIgnoreCaseAndWorkspace(String name, Workspace workspace);

    List<Category> findByUserIsNull();

    List<Category> findByUser(User user);

    List<Category> findByUserOrUserIsNull(User user);

    boolean existsByNameIgnoreCaseAndUserIsNull(String name);

    boolean existsByNameIgnoreCaseAndUser(String name, User user);

    Optional<Category> findByNameIgnoreCaseAndUserIsNull(String name);

    Optional<Category> findByNameIgnoreCaseAndUser(String name, User user);
}
