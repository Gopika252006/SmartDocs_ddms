package com.smartdocs.controller;

import com.smartdocs.dto.SecurityCenterResponse;
import com.smartdocs.entity.LoginHistory;
import com.smartdocs.entity.User;
import com.smartdocs.exception.ResourceNotFoundException;
import com.smartdocs.repository.LoginHistoryRepository;
import com.smartdocs.repository.UserRepository;
import com.smartdocs.service.SecurityCenterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/security")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SecurityCenterController {

    @Autowired
    private SecurityCenterService securityService;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/status")
    public ResponseEntity<SecurityCenterResponse> getSecurityStatus(Authentication authentication) {
        String email = authentication.getName();
        SecurityCenterResponse response = securityService.getSecurityStatus(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/login-history")
    public ResponseEntity<List<Map<String, Object>>> getLoginHistory(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<LoginHistory> historyList = loginHistoryRepository.findTop10ByUserOrderByCreatedAtDesc(user);

        List<Map<String, Object>> response = historyList.stream().map(h -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", h.getId());
            map.put("ipAddress", h.getIpAddress());
            map.put("browser", h.getBrowser());
            map.put("os", h.getOs());
            map.put("device", h.getDevice());
            map.put("location", h.getLocation());
            map.put("createdAt", h.getCreatedAt());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
