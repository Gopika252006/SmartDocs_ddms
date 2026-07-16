package com.smartdocs.service;

import com.smartdocs.dto.FolderRequest;
import com.smartdocs.dto.FolderResponse;

import java.util.List;

public interface FolderService {
    FolderResponse createFolder(FolderRequest request, String userEmail);
    List<FolderResponse> getRootFolders(String userEmail);
    List<FolderResponse> getSubfolders(Long parentId, String userEmail);
    FolderResponse getFolderDetails(Long folderId, String userEmail);
    FolderResponse renameFolder(Long folderId, String newName, String userEmail);
    void softDeleteFolder(Long folderId, String userEmail);
    List<FolderResponse> getAllFolders(String userEmail);
}
