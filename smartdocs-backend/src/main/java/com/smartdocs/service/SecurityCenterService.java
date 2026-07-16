package com.smartdocs.service;

import com.smartdocs.dto.SecurityCenterResponse;

public interface SecurityCenterService {
    SecurityCenterResponse getSecurityStatus(String userEmail);
}
