package com.smartdocs.service;

import com.smartdocs.dto.DocResponse;
import com.smartdocs.dto.DocVersionResponse;
import com.smartdocs.dto.ShareRequest;
import com.smartdocs.dto.ShareResponse;
import com.smartdocs.entity.*;
import com.smartdocs.exception.BadRequestException;
import com.smartdocs.exception.ResourceNotFoundException;
import com.smartdocs.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocServiceImpl implements DocService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AIService aiService;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShareRepository shareRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DocumentAiMetadataRepository aiMetadataRepository;

    @Autowired
    private MailService mailService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 150 * 1024 * 1024; // 150 MB
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "png", "jpg", "jpeg", "webp", "bmp", "gif", "mp3", "wav", "aac", "m4a", "ogg", "mp4", "avi", "mov", "mkv", "webm", "zip", "java", "py", "js", "cpp", "c", "html", "css"
    ));

    @Override
    @Transactional
    public DocResponse uploadDocument(MultipartFile file, String name, Long folderId, Long categoryId, Set<String> tags, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateFile(file);

        // Storage limit validation
        long incomingSize = file.getSize();
        Workspace workspace = user.getWorkspace();
        if (workspace != null) {
            Long currentUsed = documentRepository.getStorageUsedByWorkspace(workspace);
            if (currentUsed == null) {
                currentUsed = 0L;
            }
            Long maxLimit = workspace.getMaxStorageLimit() != null ? workspace.getMaxStorageLimit() : 
                ("PERSONAL".equalsIgnoreCase(workspace.getWorkspaceType()) ? 100L * 1024 * 1024 : 50L * 1024 * 1024 * 1024);

            if (currentUsed + incomingSize > maxLimit) {
                long remaining = Math.max(0L, maxLimit - currentUsed);
                String remainingStr = formatSize(remaining);
                throw new BadRequestException("Workspace storage limit exceeded. Available space: " + remainingStr + ".");
            }
        } else {
            // For users without workspace (Super Admin fallback / user fallback)
            Long currentUsed = documentRepository.getStorageUsedByUser(user);
            if (currentUsed == null) {
                currentUsed = 0L;
            }
            long maxLimit = 100L * 1024 * 1024; // Default limit for personal files
            if (currentUsed + incomingSize > maxLimit) {
                long remaining = Math.max(0L, maxLimit - currentUsed);
                String remainingStr = formatSize(remaining);
                throw new BadRequestException("User storage limit exceeded. Available space: " + remainingStr + ".");
            }
        }

        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
            if (user.getWorkspace() != null) {
                if (folder.getWorkspace() == null || !folder.getWorkspace().getId().equals(user.getWorkspace().getId())) {
                    throw new BadRequestException("Access denied: Folder is not in your workspace");
                }
            } else if (!folder.getUser().getEmail().equals(userEmail)) {
                throw new BadRequestException("Access denied: You do not own this folder");
            }
        }

        Category category = null;
        if (categoryId != null) {
            category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }

        String fileName = name != null && !name.trim().isEmpty() ? name.trim() : file.getOriginalFilename();
        String fileExtension = getFileExtension(file.getOriginalFilename());
        if (!fileName.toLowerCase().endsWith("." + fileExtension)) {
            fileName += "." + fileExtension;
        }

        Optional<Document> existingDocOpt;
        if (user.getWorkspace() != null) {
            if (folder != null) {
                existingDocOpt = documentRepository.findByNameAndFolderAndWorkspaceAndIsDeletedFalse(fileName, folder, user.getWorkspace());
            } else {
                existingDocOpt = documentRepository.findByNameAndFolderIsNullAndWorkspaceAndIsDeletedFalse(fileName, user.getWorkspace());
            }
        } else {
            if (folder != null) {
                existingDocOpt = documentRepository.findByNameAndFolderAndUserAndIsDeletedFalse(fileName, folder, user);
            } else {
                existingDocOpt = documentRepository.findByNameAndFolderIsNullAndUserAndIsDeletedFalse(fileName, user);
            }
        }

        if (existingDocOpt.isPresent()) {
            return uploadNewVersion(existingDocOpt.get().getId(), file, userEmail);
        }

        String storedFileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path targetLocation = getUploadPath().resolve(storedFileName);

        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file. Please try again!", ex);
        }

        Document document = Document.builder()
                .name(fileName)
                .filePath(targetLocation.toString())
                .fileType(fileExtension.toUpperCase())
                .fileSize(file.getSize())
                .folder(folder)
                .category(category)
                .user(user)
                .workspace(user.getWorkspace())
                .tags(tags != null ? tags : new HashSet<>())
                .isDeleted(false)
                .build();

        documentRepository.save(document);

        DocumentVersion version = DocumentVersion.builder()
                .document(document)
                .versionNumber(1)
                .filePath(targetLocation.toString())
                .fileSize(file.getSize())
                .uploadedBy(user)
                .build();
        versionRepository.save(version);

        AuditLog audit = AuditLog.builder()
                .user(user)
                .document(document)
                .action("UPLOAD_DOC: " + document.getName() + " (v1)")
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        Notification adminNotif = Notification.builder()
                .message("New Document Uploaded: " + document.getName() + " by " + user.getName())
                .type("ADMIN")
                .workspace(user.getWorkspace())
                .build();
        notificationRepository.save(adminNotif);

        Notification userNotif = Notification.builder()
                .user(user)
                .message("Document uploaded successfully: " + document.getName() + ". AI processing started...")
                .type("USER")
                .build();
        notificationRepository.save(userNotif);

        // Trigger AI processing synchronously so the frontend gets metadata and security results immediately on upload
        try {
            aiService.processDocumentEntity(document);
        } catch (Exception e) {
            System.err.println("Background AI document processing failed: " + e.getMessage());
        }

        return mapToResponse(document);
    }

    @Override
    @Transactional
    public DocResponse uploadNewVersion(Long docId, MultipartFile file, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document document = documentRepository.findByIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("Access denied: You do not own this document");
        }

        validateFile(file);

        // Storage limit validation
        long sizeDifference = file.getSize() - document.getFileSize();
        Workspace workspace = user.getWorkspace();
        if (workspace != null) {
            Long currentUsed = documentRepository.getStorageUsedByWorkspace(workspace);
            if (currentUsed == null) {
                currentUsed = 0L;
            }
            Long maxLimit = workspace.getMaxStorageLimit() != null ? workspace.getMaxStorageLimit() : 
                ("PERSONAL".equalsIgnoreCase(workspace.getWorkspaceType()) ? 100L * 1024 * 1024 : 50L * 1024 * 1024 * 1024);

            if (currentUsed + sizeDifference > maxLimit) {
                long remaining = Math.max(0L, maxLimit - currentUsed);
                String remainingStr = formatSize(remaining);
                throw new BadRequestException("Workspace storage limit exceeded. Available space: " + remainingStr + ".");
            }
        } else {
            // For users without workspace (Super Admin fallback / user fallback)
            Long currentUsed = documentRepository.getStorageUsedByUser(user);
            if (currentUsed == null) {
                currentUsed = 0L;
            }
            long maxLimit = 100L * 1024 * 1024; // Default limit for personal files
            if (currentUsed + sizeDifference > maxLimit) {
                long remaining = Math.max(0L, maxLimit - currentUsed);
                String remainingStr = formatSize(remaining);
                throw new BadRequestException("User storage limit exceeded. Available space: " + remainingStr + ".");
            }
        }

        // Increment version number
        List<DocumentVersion> versions = versionRepository.findByDocumentOrderByVersionNumberDesc(document);
        int nextVersionNum = versions.isEmpty() ? 1 : versions.get(0).getVersionNumber() + 1;

        String storedFileName = UUID.randomUUID().toString() + "_v" + nextVersionNum + "_" + file.getOriginalFilename();
        Path targetLocation = getUploadPath().resolve(storedFileName);

        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file version. Please try again!", ex);
        }

        // Update main document properties with new version
        document.setFilePath(targetLocation.toString());
        document.setFileSize(file.getSize());
        documentRepository.save(document);

        // Save new version
        DocumentVersion version = DocumentVersion.builder()
                .document(document)
                .versionNumber(nextVersionNum)
                .filePath(targetLocation.toString())
                .fileSize(file.getSize())
                .uploadedBy(user)
                .build();
        versionRepository.save(version);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(user)
                .document(document)
                .action("UPLOAD_VERSION: " + document.getName() + " (v" + nextVersionNum + ")")
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        // Trigger AI processing synchronously for the new version content
        try {
            aiService.processDocumentEntity(document);
        } catch (Exception e) {
            System.err.println("AI version processing failed: " + e.getMessage());
        }

        return mapToResponse(document);
    }

    @Override
    @Transactional
    public DocResponse renameDocument(Long docId, String newName, String userEmail) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        User currentUser = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isOwner = document.getUser().getEmail().equals(userEmail);
        boolean isAdmin = currentUser.getRole() == com.smartdocs.entity.Role.ADMIN && 
                          document.getWorkspace() != null && 
                          currentUser.getWorkspace() != null && 
                          document.getWorkspace().getId().equals(currentUser.getWorkspace().getId());
        boolean isSuperAdmin = currentUser.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN;

        if (!isOwner && !isAdmin && !isSuperAdmin) {
            throw new BadRequestException("Access denied: You do not have permission to modify this document");
        }

        String fileExtension = getFileExtension(document.getName());
        String name = newName.trim();
        if (!name.toLowerCase().endsWith("." + fileExtension.toLowerCase())) {
            name += "." + fileExtension;
        }

        String oldName = document.getName();
        document.setName(name);
        documentRepository.save(document);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(document.getUser())
                .document(document)
                .action("RENAME_DOC: " + oldName + " -> " + name)
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        return mapToResponse(document);
    }

    @Override
    @Transactional
    public void softDeleteDocument(Long docId, String userEmail) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        User currentUser = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isOwner = document.getUser().getEmail().equals(userEmail);
        boolean isAdmin = currentUser.getRole() == com.smartdocs.entity.Role.ADMIN && 
                          document.getWorkspace() != null && 
                          currentUser.getWorkspace() != null && 
                          document.getWorkspace().getId().equals(currentUser.getWorkspace().getId());
        boolean isSuperAdmin = currentUser.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN;

        if (!isOwner && !isAdmin && !isSuperAdmin) {
            throw new BadRequestException("Access denied: You do not have permission to modify this document");
        }

        document.setDeleted(true);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(document.getUser())
                .document(document)
                .action("SOFT_DELETE_DOC: " + document.getName())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        // Notify User
        Notification userNotif = Notification.builder()
                .user(document.getUser())
                .message("Document moved to Trash: " + document.getName())
                .type("USER")
                .build();
        notificationRepository.save(userNotif);
    }

    @Override
    @Transactional
    public void restoreDocument(Long docId, String userEmail) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.isDeleted()) {
            throw new BadRequestException("Document is not in the Trash");
        }

        User currentUser = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isOwner = document.getUser().getEmail().equals(userEmail);
        boolean isAdmin = currentUser.getRole() == com.smartdocs.entity.Role.ADMIN && 
                          document.getWorkspace() != null && 
                          currentUser.getWorkspace() != null && 
                          document.getWorkspace().getId().equals(currentUser.getWorkspace().getId());
        boolean isSuperAdmin = currentUser.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN;

        if (!isOwner && !isAdmin && !isSuperAdmin) {
            throw new BadRequestException("Access denied: You do not have permission to modify this document");
        }

        document.setDeleted(false);
        document.setDeletedAt(null);
        documentRepository.save(document);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(document.getUser())
                .document(document)
                .action("RESTORE_DOC: " + document.getName())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);
    }

    @Override
    @Transactional
    public void permanentDeleteDocument(Long docId, String userEmail) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        User currentUser = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isOwner = document.getUser().getEmail().equals(userEmail);
        boolean isAdmin = currentUser.getRole() == com.smartdocs.entity.Role.ADMIN && 
                          document.getWorkspace() != null && 
                          currentUser.getWorkspace() != null && 
                          document.getWorkspace().getId().equals(currentUser.getWorkspace().getId());
        boolean isSuperAdmin = currentUser.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN;

        if (!isOwner && !isAdmin && !isSuperAdmin) {
            throw new BadRequestException("Access denied: You do not have permission to modify this document");
        }

        // Delete all physical files for versions
        List<DocumentVersion> versions = versionRepository.findAllByDocument(document);
        for (DocumentVersion version : versions) {
            deletePhysicalFile(version.getFilePath());
        }

        // Delete version records, share records, and main document record
        versionRepository.deleteAll(versions);
        List<Share> shares = shareRepository.findAllByDocument(document);
        shareRepository.deleteAll(shares);
        auditLogRepository.nullifyDocumentReferences(document);
        documentRepository.delete(document);

        // Audit Log (without entity mapping because document is hard deleted)
        AuditLog audit = AuditLog.builder()
                .user(document.getUser())
                .action("PERMANENT_DELETE_DOC: " + document.getName())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);
    }

    @Override
    public List<DocResponse> getDocumentsInFolder(Long folderId, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        List<Document> docs;
        if (user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            docs = documentRepository.findAllByFolderAndIsDeletedFalse(folder);
        } else {
            com.smartdocs.entity.Workspace workspace = user.getWorkspace();
            if (workspace != null) {
                if (user.getRole() == com.smartdocs.entity.Role.EMPLOYEE) {
                    docs = documentRepository.findByWorkspaceAndFolderAndIsDeletedFalse(workspace, folder).stream()
                            .filter(d -> d.getUser() != null && d.getUser().getId().equals(user.getId()))
                            .collect(Collectors.toList());
                } else {
                    docs = documentRepository.findByWorkspaceAndFolderAndIsDeletedFalse(workspace, folder);
                }
            } else {
                docs = documentRepository.findByUserAndFolderAndIsDeletedFalse(user, folder);
            }
        }
        
        return docs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DocResponse> getRootDocuments(String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Document> docs;
        if (user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            docs = documentRepository.findAll().stream()
                    .filter(d -> d.getFolder() == null && !d.isDeleted())
                    .collect(Collectors.toList());
        } else {
            com.smartdocs.entity.Workspace workspace = user.getWorkspace();
            if (workspace != null) {
                if (user.getRole() == com.smartdocs.entity.Role.EMPLOYEE) {
                    docs = documentRepository.findByWorkspaceAndFolderIsNullAndIsDeletedFalse(workspace).stream()
                            .filter(d -> d.getUser() != null && d.getUser().getId().equals(user.getId()))
                            .collect(Collectors.toList());
                } else {
                    docs = documentRepository.findByWorkspaceAndFolderIsNullAndIsDeletedFalse(workspace);
                }
            } else {
                docs = documentRepository.findByUserAndFolderIsNullAndIsDeletedFalse(user);
            }
        }
        
        return docs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DocResponse> getTrashedDocuments(String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Document> docs;
        if (user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            docs = documentRepository.findByIsDeletedTrue();
        } else {
            com.smartdocs.entity.Workspace workspace = user.getWorkspace();
            if (workspace != null) {
                if (user.getRole() == com.smartdocs.entity.Role.EMPLOYEE) {
                    docs = documentRepository.findByWorkspaceAndIsDeletedTrue(workspace).stream()
                            .filter(d -> d.getUser() != null && d.getUser().getId().equals(user.getId()))
                            .collect(Collectors.toList());
                } else {
                    docs = documentRepository.findByWorkspaceAndIsDeletedTrue(workspace);
                }
            } else {
                docs = documentRepository.findByUserAndIsDeletedTrue(user);
            }
        }
        
        return docs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DocVersionResponse> getDocumentVersions(Long docId, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document document = documentRepository.findByIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        boolean hasAccess = false;
        if (user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            hasAccess = true;
        } else if (document.getWorkspace() != null) {
            hasAccess = user.getWorkspace() != null && document.getWorkspace().getId().equals(user.getWorkspace().getId());
        } else {
            hasAccess = document.getUser().getEmail().equals(userEmail);
        }
        
        hasAccess = hasAccess || shareRepository.existsByDocumentAndSharedWithEmail(document, userEmail);

        if (!hasAccess) {
            throw new BadRequestException("Access denied: You do not have permissions to view version history");
        }

        return versionRepository.findByDocumentOrderByVersionNumberDesc(document)
                .stream()
                .map(v -> DocVersionResponse.builder()
                        .id(v.getId())
                        .versionNumber(v.getVersionNumber())
                        .fileSize(v.getFileSize())
                        .uploadedByName(v.getUploadedBy().getName())
                        .uploadedByEmail(v.getUploadedBy().getEmail())
                        .createdAt(v.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ShareResponse shareDocument(Long docId, ShareRequest request, String userEmail) {
        User owner = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document document = documentRepository.findByIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("Access denied: Only document owners can share documents");
        }

        if (document.isSensitive()) {
            throw new BadRequestException("Access denied: Documents marked as SENSITIVE cannot be shared to prevent potential security leaks.");
        }

        if (request.getEmail().equalsIgnoreCase(userEmail)) {
            throw new BadRequestException("You cannot share a document with yourself");
        }

        // Check if already shared
        if (shareRepository.existsByDocumentAndSharedWithEmail(document, request.getEmail())) {
            throw new BadRequestException("Document is already shared with this user");
        }

        Share share = Share.builder()
                .document(document)
                .sharedWithEmail(request.getEmail())
                .permission(request.getPermission())
                .sharedBy(owner)
                .build();
        shareRepository.save(share);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(owner)
                .document(document)
                .action("SHARE_DOC: " + document.getName() + " shared with " + request.getEmail())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        // Save notification for recipient (if user exists in DB)
        Optional<User> recipientOpt = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail());
        if (recipientOpt.isPresent()) {
            Notification userNotif = Notification.builder()
                    .user(recipientOpt.get())
                    .message("A document has been shared with you: " + document.getName() + " by " + owner.getName())
                    .type("USER")
                    .build();
            notificationRepository.save(userNotif);
        }

        mailService.sendNotificationEmail(request.getEmail(), "SmartDocs - Document Shared",
                "Hello,\n\n" + owner.getName() + " (" + owner.getEmail() + ") has shared a document with you on SmartDocs:\n\n" +
                "Document Name: " + document.getName() + "\n" +
                "Permission: " + request.getPermission() + "\n\n" +
                "Log into your SmartDocs account and check 'Shared with Me' to view the document.");

        return ShareResponse.builder()
                .id(share.getId())
                .documentId(document.getId())
                .documentName(document.getName())
                .sharedWithEmail(share.getSharedWithEmail())
                .permission(share.getPermission())
                .sharedByName(owner.getName())
                .sharedByEmail(owner.getEmail())
                .createdAt(share.getCreatedAt())
                .build();
    }

    @Override
    public List<ShareResponse> getSharedWithMe(String userEmail) {
        return shareRepository.findBySharedWithEmail(userEmail)
                .stream()
                .map(share -> ShareResponse.builder()
                        .id(share.getId())
                        .documentId(share.getDocument().getId())
                        .documentName(share.getDocument().getName())
                        .sharedWithEmail(share.getSharedWithEmail())
                        .permission(share.getPermission())
                        .sharedByName(share.getSharedBy().getName())
                        .sharedByEmail(share.getSharedBy().getEmail())
                        .createdAt(share.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<ShareResponse> getSharedByMe(String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return shareRepository.findBySharedBy(user)
                .stream()
                .map(share -> ShareResponse.builder()
                        .id(share.getId())
                        .documentId(share.getDocument().getId())
                        .documentName(share.getDocument().getName())
                        .sharedWithEmail(share.getSharedWithEmail())
                        .permission(share.getPermission())
                        .sharedByName(share.getSharedBy().getName())
                        .sharedByEmail(share.getSharedBy().getEmail())
                        .createdAt(share.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Resource loadFileAsResource(Long docId, Integer versionNumber, String userEmail) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Check access: must be owner, or shared with user, or admin
        User accessor = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isOwner = document.getUser().getEmail().equals(userEmail);
        boolean isShared = shareRepository.existsByDocumentAndSharedWithEmail(document, userEmail);
        boolean isWorkspaceAdmin = accessor.getRole() == com.smartdocs.entity.Role.ADMIN && 
                                   document.getWorkspace() != null && 
                                   accessor.getWorkspace() != null && 
                                   document.getWorkspace().getId().equals(accessor.getWorkspace().getId());
        boolean isSuperAdmin = accessor.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN;

        if (document.isSensitive()) {
            // For sensitive files, ignore shares and restrict strictly to Owner, Workspace Admin, and Super Admin
            if (!isOwner && !isWorkspaceAdmin && !isSuperAdmin) {
                throw new BadRequestException("Access denied: This document is classified as SENSITIVE. Only the owner or workspace administrator can access it.");
            }
        } else {
            // Standard access check
            if (!isOwner && !isShared && !isWorkspaceAdmin && !isSuperAdmin) {
                throw new BadRequestException("Access denied: You do not have permission to view/download this file");
            }
        }

        String targetFilePath = document.getFilePath();

        // If a specific version is requested
        if (versionNumber != null) {
            DocumentVersion version = versionRepository.findByDocumentAndVersionNumber(document, versionNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Document version not found"));
            targetFilePath = version.getFilePath();
        }

        try {
            Path filePath = Paths.get(targetFilePath);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                // Audit Log
                AuditLog audit = AuditLog.builder()
                        .user(accessor)
                        .document(document)
                        .action("DOWNLOAD_DOC: " + document.getName() + (versionNumber != null ? " (v" + versionNumber + ")" : ""))
                        .ipAddress("127.0.0.1")
                        .browser("Browser")
                        .os("System")
                        .device("Desktop")
                        .build();
                auditLogRepository.save(audit);

                return resource;
            } else {
                throw new ResourceNotFoundException("Physical file not found on disk");
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("File path resolve error");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds maximum limit of 150 MB");
        }
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 Bytes";
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", (double) bytes / 1024);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", (double) bytes / (1024 * 1024));
        } else {
            return String.format("%.1f GB", (double) bytes / (1024 * 1024 * 1024));
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastIndex = fileName.lastIndexOf('.');
        if (lastIndex == -1) return "";
        return fileName.substring(lastIndex + 1).toLowerCase();
    }

    private Path getUploadPath() {
        try {
            Path path = Paths.get(uploadDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Could not create directories for file uploads", e);
        }
    }

    private void deletePhysicalFile(String filePathStr) {
        if (filePathStr == null || filePathStr.isEmpty()) return;
        try {
            File file = new File(filePathStr);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    System.out.println("Physical file deleted: " + filePathStr);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to delete physical file " + filePathStr + ": " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteDocumentVersion(Long docId, Integer versionNumber, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document document = documentRepository.findByIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("Access denied: You do not own this document");
        }

        // Find the specific version
        DocumentVersion targetVersion = versionRepository.findByDocumentAndVersionNumber(document, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Document version v" + versionNumber + " not found"));

        // Delete the physical file
        deletePhysicalFile(targetVersion.getFilePath());

        // Delete the database version record
        versionRepository.delete(targetVersion);

        // Fetch remaining versions
        List<DocumentVersion> remainingVersions = versionRepository.findByDocumentOrderByVersionNumberDesc(document);

        if (remainingVersions.isEmpty()) {
            // If all versions are deleted, delete the document entirely
            List<Share> shares = shareRepository.findAllByDocument(document);
            shareRepository.deleteAll(shares);
            documentRepository.delete(document);
        } else {
            // Update the main document with the latest remaining version details
            DocumentVersion latestVersion = remainingVersions.get(0);
            document.setFilePath(latestVersion.getFilePath());
            document.setFileSize(latestVersion.getFileSize());
            documentRepository.save(document);
        }

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(user)
                .document(document)
                .action("DELETE_VERSION: " + document.getName() + " (v" + versionNumber + ")")
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);
    }

    private DocResponse mapToResponse(Document document) {
        String summary = document.getAiSummary();
        String highlights = document.getAiHighlights();
        String keywords = document.getAiKeywords();
        String category = document.getAiCategory();
        String confidentiality = document.getConfidentialityClass();
        boolean sensitive = document.isSensitive();

        if (summary == null || summary.trim().isEmpty() || summary.contains("processing") || summary.contains("failed") || summary.contains("timed out")) {
            DocumentAiMetadata aiMeta = aiMetadataRepository.findByDocument(document).orElse(null);
            if (aiMeta != null && "COMPLETED".equalsIgnoreCase(aiMeta.getStatus())) {
                summary = aiMeta.getSummary();
                highlights = aiMeta.getImportantPoints();
                keywords = aiMeta.getKeywords();
                category = aiMeta.getCategory();
                confidentiality = "Public";
                sensitive = false;
            }
        }

        return DocResponse.builder()
                .id(document.getId())
                .name(document.getName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .folderId(document.getFolder() != null ? document.getFolder().getId() : null)
                .folderName(document.getFolder() != null ? document.getFolder().getName() : null)
                .categoryId(document.getCategory() != null ? document.getCategory().getId() : null)
                .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                .userId(document.getUser().getId())
                .uploaderName(document.getUser().getName())
                .uploaderEmail(document.getUser().getEmail())
                .tags(document.getTags())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .isDeleted(document.isDeleted())
                .aiSummary(summary)
                .aiHighlights(highlights)
                .aiKeywords(keywords)
                .aiMetadata(document.getAiMetadata())
                .aiCategory(category)
                .duplicateStatus(document.getDuplicateStatus())
                .duplicateScore(document.getDuplicateScore())
                .relatedDocumentIds(document.getRelatedDocumentIds())
                .isSensitive(sensitive)
                .piiDetected(document.isPiiDetected())
                .confidentialityClass(confidentiality)
                .sensitiveDetails(document.getSensitiveDetails())
                .build();
    }

    @Override
    @Transactional
    public DocResponse moveDocument(Long docId, Long folderId, String userEmail) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isOwner = document.getUser().getEmail().equals(userEmail);
        boolean isAdmin = user.getRole() == com.smartdocs.entity.Role.ADMIN && 
                          document.getWorkspace() != null && 
                          user.getWorkspace() != null && 
                          document.getWorkspace().getId().equals(user.getWorkspace().getId());
        boolean isSuperAdmin = user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN;

        if (!isOwner && !isAdmin && !isSuperAdmin) {
            throw new BadRequestException("Access denied: You do not have permission to move this document");
        }

        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Target folder not found"));

            if (document.getWorkspace() != null && folder.getWorkspace() != null &&
                    !document.getWorkspace().getId().equals(folder.getWorkspace().getId())) {
                throw new BadRequestException("Cannot move document to a folder in another workspace");
            }
        }

        document.setFolder(folder);
        documentRepository.save(document);

        AuditLog audit = AuditLog.builder()
                .user(user)
                .document(document)
                .action("MOVE_DOC: " + document.getName() + " -> " + (folder != null ? folder.getName() : "Root"))
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        return mapToResponse(document);
    }
}
