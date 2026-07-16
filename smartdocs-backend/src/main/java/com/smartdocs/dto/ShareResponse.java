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
public class ShareResponse {
    private Long id;
    private Long documentId;
    private String documentName;
    private String sharedWithEmail;
    private String permission;
    private String sharedByName;
    private String sharedByEmail;
    private LocalDateTime createdAt;
}
