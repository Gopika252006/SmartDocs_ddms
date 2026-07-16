package com.smartdocs.service;

import com.smartdocs.dto.FolderRequest;
import com.smartdocs.dto.FolderResponse;
import com.smartdocs.entity.AuditLog;
import com.smartdocs.entity.Document;
import com.smartdocs.entity.Folder;
import com.smartdocs.entity.User;
import com.smartdocs.exception.BadRequestException;
import com.smartdocs.exception.ResourceNotFoundException;
import com.smartdocs.repository.AuditLogRepository;
import com.smartdocs.repository.DocumentRepository;
import com.smartdocs.repository.FolderRepository;
import com.smartdocs.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class FolderServiceImpl implements FolderService {

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public FolderResponse createFolder(FolderRequest request, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        com.smartdocs.entity.Workspace workspace = user.getWorkspace();

        Folder parentFolder = null;
        if (request.getParentFolderId() != null) {
            parentFolder = folderRepository.findByIdAndDeletedAtIsNull(request.getParentFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found"));
        }

        // Validate duplicate folder
        boolean exists;
        if (workspace != null) {
            exists = (parentFolder != null)
                    ? folderRepository.existsByNameAndParentFolderAndWorkspaceAndDeletedAtIsNull(request.getName(), parentFolder, workspace)
                    : folderRepository.existsByNameAndParentFolderIsNullAndWorkspaceAndDeletedAtIsNull(request.getName(), workspace);
        } else {
            exists = (parentFolder != null)
                    ? folderRepository.existsByNameAndParentFolderAndUserAndDeletedAtIsNull(request.getName(), parentFolder, user)
                    : folderRepository.existsByNameAndParentFolderIsNullAndUserAndDeletedAtIsNull(request.getName(), user);
        }

        if (exists) {
            throw new BadRequestException("Folder with name '" + request.getName() + "' already exists in this directory");
        }

        Folder folder = Folder.builder()
                .name(request.getName())
                .parentFolder(parentFolder)
                .user(user)
                .workspace(workspace)
                .build();

        folderRepository.save(folder);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(user)
                .action("CREATE_FOLDER: " + folder.getName())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        return mapToResponse(folder);
    }

    @Override
    public List<FolderResponse> getRootFolders(String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        com.smartdocs.entity.Workspace workspace = user.getWorkspace();
        List<Folder> folders;
        if (workspace != null) {
            folders = folderRepository.findByWorkspaceAndParentFolderIsNullAndDeletedAtIsNull(workspace);
        } else {
            folders = folderRepository.findByUserAndParentFolderIsNullAndDeletedAtIsNull(user);
        }
        return folders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<FolderResponse> getSubfolders(Long parentId, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Folder parent = folderRepository.findByIdAndDeletedAtIsNull(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        com.smartdocs.entity.Workspace workspace = user.getWorkspace();
        List<Folder> folders;
        if (workspace != null) {
            folders = folderRepository.findByWorkspaceAndParentFolderAndDeletedAtIsNull(workspace, parent);
        } else {
            folders = folderRepository.findByUserAndParentFolderAndDeletedAtIsNull(user, parent);
        }
        return folders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public FolderResponse getFolderDetails(Long folderId, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getWorkspace() != null) {
            if (!folder.getWorkspace().getId().equals(user.getWorkspace().getId())) {
                throw new BadRequestException("Access denied: You do not own this folder");
            }
        } else if (!folder.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("Access denied: You do not own this folder");
        }

        return mapToResponse(folder);
    }

    @Override
    @Transactional
    public FolderResponse renameFolder(Long folderId, String newName, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getWorkspace() != null) {
            if (!folder.getWorkspace().getId().equals(user.getWorkspace().getId())) {
                throw new BadRequestException("Access denied: You do not own this folder");
            }
        } else if (!folder.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("Access denied: You do not own this folder");
        }

        // Validate duplicates for rename
        boolean exists;
        if (folder.getWorkspace() != null) {
            exists = (folder.getParentFolder() != null)
                    ? folderRepository.existsByNameAndParentFolderAndWorkspaceAndDeletedAtIsNull(newName, folder.getParentFolder(), folder.getWorkspace())
                    : folderRepository.existsByNameAndParentFolderIsNullAndWorkspaceAndDeletedAtIsNull(newName, folder.getWorkspace());
        } else {
            exists = (folder.getParentFolder() != null)
                    ? folderRepository.existsByNameAndParentFolderAndUserAndDeletedAtIsNull(newName, folder.getParentFolder(), folder.getUser())
                    : folderRepository.existsByNameAndParentFolderIsNullAndUserAndDeletedAtIsNull(newName, folder.getUser());
        }

        if (exists && !folder.getName().equalsIgnoreCase(newName)) {
            throw new BadRequestException("Folder with name '" + newName + "' already exists in this directory");
        }

        String oldName = folder.getName();
        folder.setName(newName);
        folderRepository.save(folder);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(user)
                .action("RENAME_FOLDER: " + oldName + " -> " + newName)
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        return mapToResponse(folder);
    }

    @Override
    @Transactional
    public void softDeleteFolder(Long folderId, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getWorkspace() != null) {
            if (!folder.getWorkspace().getId().equals(user.getWorkspace().getId())) {
                throw new BadRequestException("Access denied: You do not own this folder");
            }
        } else if (!folder.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("Access denied: You do not own this folder");
        }

        LocalDateTime now = LocalDateTime.now();
        softDeleteFolderRecursive(folder, now);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(user)
                .action("DELETE_FOLDER: " + folder.getName())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);
    }

    private void softDeleteFolderRecursive(Folder folder, LocalDateTime now) {
        folder.setDeletedAt(now);
        folderRepository.save(folder);

        // Soft-delete documents
        List<Document> docs = documentRepository.findAllByFolderAndIsDeletedFalse(folder);
        for (Document doc : docs) {
            doc.setDeleted(true);
            doc.setDeletedAt(now);
            documentRepository.save(doc);
        }

        // Recursive delete child folders
        List<Folder> subfolders;
        if (folder.getWorkspace() != null) {
            subfolders = folderRepository.findByWorkspaceAndParentFolderAndDeletedAtIsNull(folder.getWorkspace(), folder);
        } else {
            subfolders = folderRepository.findByUserAndParentFolderAndDeletedAtIsNull(folder.getUser(), folder);
        }
        for (Folder sub : subfolders) {
            softDeleteFolderRecursive(sub, now);
        }
    }

    @Override
    public List<FolderResponse> getAllFolders(String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        com.smartdocs.entity.Workspace workspace = user.getWorkspace();
        List<Folder> folders;
        if (workspace != null) {
            folders = folderRepository.findByWorkspaceAndDeletedAtIsNull(workspace);
        } else {
            folders = folderRepository.findByUserAndDeletedAtIsNull(user);
        }
        return folders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private FolderResponse mapToResponse(Folder folder) {
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .parentFolderName(folder.getParentFolder() != null ? folder.getParentFolder().getName() : null)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}
