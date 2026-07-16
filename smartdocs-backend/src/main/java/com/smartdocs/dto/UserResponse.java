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
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private boolean emailVerified;
    private boolean active;
    private boolean firstLogin;
    private LocalDateTime createdAt;
    private Long workspaceId;
    private String workspaceName;
    private String workspaceType;
}
