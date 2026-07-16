package com.smartdocs.repository;

import com.smartdocs.entity.Category;
import com.smartdocs.entity.Document;
import com.smartdocs.entity.Folder;
import com.smartdocs.entity.User;
import com.smartdocs.entity.Workspace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndIsDeletedFalse(Long id);

    Optional<Document> findByNameAndFolderAndUserAndIsDeletedFalse(String name, Folder folder, User user);

    Optional<Document> findByNameAndFolderIsNullAndUserAndIsDeletedFalse(String name, User user);

    Optional<Document> findByNameAndFolderAndWorkspaceAndIsDeletedFalse(String name, Folder folder, Workspace workspace);

    Optional<Document> findByNameAndFolderIsNullAndWorkspaceAndIsDeletedFalse(String name, Workspace workspace);

    List<Document> findByUserAndIsDeletedFalse(User user);

    List<Document> findByWorkspace(Workspace workspace);

    List<Document> findByWorkspaceAndIsDeletedFalse(Workspace workspace);

    List<Document> findByWorkspaceAndFolderAndIsDeletedFalse(Workspace workspace, Folder folder);

    List<Document> findByWorkspaceAndFolderIsNullAndIsDeletedFalse(Workspace workspace);

    List<Document> findByWorkspaceAndIsDeletedTrue(Workspace workspace);

    List<Document> findByUserAndFolderAndIsDeletedFalse(User user, Folder folder);

    List<Document> findByUserAndFolderIsNullAndIsDeletedFalse(User user);

    List<Document> findByUserAndIsDeletedTrue(User user);

    List<Document> findByIsDeletedTrue();

    @Query("SELECT SUM(d.fileSize) FROM Document d WHERE d.isDeleted = false")
    Long getTotalStorageUsed();

    @Query("SELECT SUM(d.fileSize) FROM Document d WHERE d.user = :user AND d.isDeleted = false")
    Long getStorageUsedByUser(@Param("user") User user);

    @Query("SELECT SUM(d.fileSize) FROM Document d WHERE d.workspace = :workspace AND d.isDeleted = false")
    Long getStorageUsedByWorkspace(@Param("workspace") Workspace workspace);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false ORDER BY d.fileSize DESC")
    List<Document> findLargestFiles(Pageable pageable);

    @Query("SELECT d.user.email, SUM(d.fileSize) FROM Document d WHERE d.isDeleted = false GROUP BY d.user.email ORDER BY SUM(d.fileSize) DESC")
    List<Object[]> getTopStorageConsumers(Pageable pageable);

    @Query("SELECT d.user.email, SUM(d.fileSize) FROM Document d WHERE d.workspace = :workspace AND d.isDeleted = false GROUP BY d.user.email ORDER BY SUM(d.fileSize) DESC")
    List<Object[]> getTopStorageConsumersForWorkspace(@Param("workspace") Workspace workspace, Pageable pageable);

    @Query("SELECT d.user.email, SUM(d.fileSize) FROM Document d WHERE d.user = :user AND d.isDeleted = false GROUP BY d.user.email ORDER BY SUM(d.fileSize) DESC")
    List<Object[]> getTopStorageConsumersForUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT FUNCTION('MONTHNAME', d.createdAt), COUNT(d) FROM Document d WHERE d.isDeleted = false GROUP BY FUNCTION('MONTH', d.createdAt), FUNCTION('MONTHNAME', d.createdAt)")
    List<Object[]> getMonthlyUploadStats();

    @Query("SELECT FUNCTION('MONTHNAME', d.createdAt), COUNT(d) FROM Document d WHERE d.workspace = :workspace AND d.isDeleted = false GROUP BY FUNCTION('MONTH', d.createdAt), FUNCTION('MONTHNAME', d.createdAt)")
    List<Object[]> getMonthlyUploadStatsForWorkspace(@Param("workspace") Workspace workspace);

    @Query("SELECT FUNCTION('MONTHNAME', d.createdAt), COUNT(d) FROM Document d WHERE d.user = :user AND d.isDeleted = false GROUP BY FUNCTION('MONTH', d.createdAt), FUNCTION('MONTHNAME', d.createdAt)")
    List<Object[]> getMonthlyUploadStatsForUser(@Param("user") User user);

    @Query("SELECT d.category.name, COUNT(d) FROM Document d WHERE d.isDeleted = false AND d.category IS NOT NULL GROUP BY d.category.name")
    List<Object[]> getCategoryDistribution();

    @Query("SELECT d.category.name, COUNT(d) FROM Document d WHERE d.workspace = :workspace AND d.isDeleted = false AND d.category IS NOT NULL GROUP BY d.category.name")
    List<Object[]> getCategoryDistributionForWorkspace(@Param("workspace") Workspace workspace);

    @Query("SELECT d.category.name, COUNT(d) FROM Document d WHERE d.user = :user AND d.isDeleted = false AND d.category IS NOT NULL GROUP BY d.category.name")
    List<Object[]> getCategoryDistributionForUser(@Param("user") User user);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND " +
           "(:name IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:folderId IS NULL OR d.folder.id = :folderId) AND " +
           "(:categoryId IS NULL OR d.category.id = :categoryId) AND " +
           "(:fileType IS NULL OR LOWER(d.fileType) = LOWER(:fileType)) AND " +
           "(:userId IS NULL OR d.user.id = :userId)")
    Page<Document> searchDocuments(@Param("name") String name,
                                   @Param("folderId") Long folderId,
                                   @Param("categoryId") Long categoryId,
                                   @Param("fileType") String fileType,
                                   @Param("userId") Long userId,
                                   Pageable pageable);

    List<Document> findAllByFolderAndIsDeletedFalse(Folder folder);
    
    List<Document> findAllByUserAndIsDeletedFalse(User user);
}
