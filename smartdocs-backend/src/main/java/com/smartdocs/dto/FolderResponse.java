package com.smartdocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FolderResponse {
    private Long id;
    private String name;
    private Long parentFolderId;
    private String parentFolderName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
