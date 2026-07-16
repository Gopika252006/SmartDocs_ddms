package com.smartdocs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShareRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String permission = "VIEW_ONLY"; // Extensible default
}
