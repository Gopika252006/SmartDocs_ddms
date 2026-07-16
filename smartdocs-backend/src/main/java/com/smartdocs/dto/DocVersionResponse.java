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
public class DocVersionResponse {
    private Long id;
    private Integer versionNumber;
    private Long fileSize;
    private String uploadedByName;
    private String uploadedByEmail;
    private LocalDateTime createdAt;
}
