package com.smartdocs.repository;

import com.smartdocs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    List<User> findAllByDeletedAtIsNull();

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    Page<User> findAllByWorkspaceAndDeletedAtIsNull(com.smartdocs.entity.Workspace workspace, Pageable pageable);

    long countByWorkspaceAndDeletedAtIsNull(com.smartdocs.entity.Workspace workspace);

    List<User> findAllByWorkspaceAndDeletedAtIsNull(com.smartdocs.entity.Workspace workspace);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.workspace = :workspace AND u.deletedAt IS NULL AND " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<User> searchUsersInWorkspace(@Param("workspace") com.smartdocs.entity.Workspace workspace, @Param("query") String query, Pageable pageable);

    Optional<User> findByActivationToken(String activationToken);
}
