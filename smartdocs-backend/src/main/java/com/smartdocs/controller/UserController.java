package com.smartdocs.controller;

import com.smartdocs.dto.ChangePasswordRequest;
import com.smartdocs.dto.ProfileRequest;
import com.smartdocs.dto.UserResponse;
import com.smartdocs.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        String email = authentication.getName();
        UserResponse response = userService.getProfile(email);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody ProfileRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        UserResponse response = userService.updateProfile(request, email);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        userService.changePassword(request, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Password changed successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            Authentication authentication) {
        String adminEmail = authentication.getName();
        List<UserResponse> response = userService.getAllUsers(query, page, size, adminEmail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/employee")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> createEmployee(
            @Valid @RequestBody com.smartdocs.dto.InviteUserRequest request,
            Authentication authentication) {
        String adminEmail = authentication.getName();
        UserResponse response = userService.createEmployee(request, adminEmail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/resend-invitation")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> resendInvitation(@PathVariable Long id, Authentication authentication) {
        String adminEmail = authentication.getName();
        userService.resendInvitation(id, adminEmail);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Invitation email has been resent successfully.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> resetUserPassword(@PathVariable Long id, Authentication authentication) {
        String adminEmail = authentication.getName();
        userService.resetPasswordByAdmin(id, adminEmail);
        Map<String, String> response = new HashMap<>();
        response.put("message", "A password reset link has been sent to the user.");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long id,
            @RequestParam String role,
            Authentication authentication) {
        String email = authentication.getName();
        UserResponse response = userService.updateUserRole(id, role, email);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable Long id,
            @RequestParam boolean active,
            Authentication authentication) {
        String email = authentication.getName();
        UserResponse response = userService.updateUserStatus(id, active, email);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        userService.deleteUser(id, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "User and all related files deleted successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<Map<String, Object>>> getNotifications(Authentication authentication) {
        String email = authentication.getName();
        List<Map<String, Object>> response = userService.getNotifications(email);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<?> readNotification(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        userService.markNotificationAsRead(id, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Notification marked as read");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadNotificationCount(Authentication authentication) {
        String email = authentication.getName();
        long count = userService.getUnreadNotificationCount(email);
        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", count);
        return ResponseEntity.ok(response);
    }
}
