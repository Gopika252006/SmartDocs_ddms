package com.smartdocs.repository;

import com.smartdocs.entity.AuditLog;
import com.smartdocs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findTop10ByOrderByCreatedAtDesc();

    List<AuditLog> findTop10ByUserOrderByCreatedAtDesc(User user);

    List<AuditLog> findByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:email IS NULL OR LOWER(a.user.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:action IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%'))) AND " +
           "(:browser IS NULL OR LOWER(a.browser) LIKE LOWER(CONCAT('%', :browser, '%'))) AND " +
           "(:os IS NULL OR LOWER(a.os) LIKE LOWER(CONCAT('%', :os, '%'))) AND " +
           "(:ipAddress IS NULL OR a.ipAddress LIKE CONCAT('%', :ipAddress, '%'))")
    Page<AuditLog> searchAuditLogs(@Param("email") String email,
                                   @Param("action") String action,
                                   @Param("browser") String browser,
                                   @Param("os") String os,
                                   @Param("ipAddress") String ipAddress,
                                   Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(a.user.workspace = :workspace) AND " +
           "(:email IS NULL OR LOWER(a.user.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:action IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%'))) AND " +
           "(:browser IS NULL OR LOWER(a.browser) LIKE LOWER(CONCAT('%', :browser, '%'))) AND " +
           "(:os IS NULL OR LOWER(a.os) LIKE LOWER(CONCAT('%', :os, '%'))) AND " +
           "(:ipAddress IS NULL OR a.ipAddress LIKE CONCAT('%', :ipAddress, '%'))")
    Page<AuditLog> searchAuditLogsInWorkspace(@Param("workspace") com.smartdocs.entity.Workspace workspace,
                                             @Param("email") String email,
                                             @Param("action") String action,
                                             @Param("browser") String browser,
                                             @Param("os") String os,
                                             @Param("ipAddress") String ipAddress,
                                             Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(a.user = :user) AND " +
           "(:email IS NULL OR LOWER(a.user.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:action IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%'))) AND " +
           "(:browser IS NULL OR LOWER(a.browser) LIKE LOWER(CONCAT('%', :browser, '%'))) AND " +
           "(:os IS NULL OR LOWER(a.os) LIKE LOWER(CONCAT('%', :os, '%'))) AND " +
           "(:ipAddress IS NULL OR a.ipAddress LIKE CONCAT('%', :ipAddress, '%'))")
    Page<AuditLog> searchAuditLogsForUser(@Param("user") User user,
                                         @Param("email") String email,
                                         @Param("action") String action,
                                         @Param("browser") String browser,
                                         @Param("os") String os,
                                         @Param("ipAddress") String ipAddress,
                                         Pageable pageable);

    void deleteAllByUser(User user);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE AuditLog a SET a.document = null WHERE a.document = :document")
    void nullifyDocumentReferences(@Param("document") com.smartdocs.entity.Document document);
}
