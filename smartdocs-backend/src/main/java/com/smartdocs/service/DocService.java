package com.smartdocs.service;

import com.smartdocs.dto.DocResponse;
import com.smartdocs.dto.DocVersionResponse;
import com.smartdocs.dto.ShareRequest;
import com.smartdocs.dto.ShareResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

public interface DocService {
    DocResponse uploadDocument(MultipartFile file, String name, Long folderId, Long categoryId, Set<String> tags, String userEmail);
    DocResponse uploadNewVersion(Long docId, MultipartFile file, String userEmail);
    DocResponse renameDocument(Long docId, String newName, String userEmail);
    void softDeleteDocument(Long docId, String userEmail);
    void restoreDocument(Long docId, String userEmail);
    void permanentDeleteDocument(Long docId, String userEmail);
    List<DocResponse> getDocumentsInFolder(Long folderId, String userEmail);
    List<DocResponse> getRootDocuments(String userEmail);
    List<DocResponse> getTrashedDocuments(String userEmail);
    List<DocVersionResponse> getDocumentVersions(Long docId, String userEmail);
    ShareResponse shareDocument(Long docId, ShareRequest request, String userEmail);
    List<ShareResponse> getSharedWithMe(String userEmail);
    List<ShareResponse> getSharedByMe(String userEmail);
    Resource loadFileAsResource(Long docId, Integer versionNumber, String userEmail);
    void deleteDocumentVersion(Long docId, Integer versionNumber, String userEmail);
    DocResponse moveDocument(Long docId, Long folderId, String userEmail);
}
