package com.smartdocs.service;

import com.smartdocs.dto.ChangePasswordRequest;
import com.smartdocs.dto.ProfileRequest;
import com.smartdocs.dto.UserResponse;
import com.smartdocs.dto.RegisterRequest;
import com.smartdocs.dto.InviteUserRequest;

import java.util.List;
import java.util.Map;

public interface UserService {
    UserResponse getProfile(String email);
    UserResponse updateProfile(ProfileRequest request, String email);
    void changePassword(ChangePasswordRequest request, String email);
    List<UserResponse> getAllUsers(String query, int page, int size, String adminEmail);
    UserResponse createEmployee(InviteUserRequest request, String adminEmail);
    UserResponse updateUserRole(Long userId, String role, String updaterEmail);
    UserResponse updateUserStatus(Long userId, boolean active, String updaterEmail);
    void deleteUser(Long userId, String updaterEmail);
    List<Map<String, Object>> getNotifications(String email);
    void markNotificationAsRead(Long notifId, String email);
    long getUnreadNotificationCount(String email);
    void resendInvitation(Long userId, String adminEmail);
    void resetPasswordByAdmin(Long userId, String adminEmail);
}
