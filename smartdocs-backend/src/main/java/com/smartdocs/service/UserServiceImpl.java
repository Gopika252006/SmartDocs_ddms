package com.smartdocs.service;

import com.smartdocs.dto.ChangePasswordRequest;
import com.smartdocs.dto.ProfileRequest;
import com.smartdocs.dto.UserResponse;
import com.smartdocs.dto.RegisterRequest;
import com.smartdocs.dto.InviteUserRequest;
import com.smartdocs.entity.*;
import com.smartdocs.exception.BadRequestException;
import com.smartdocs.exception.ResourceNotFoundException;
import com.smartdocs.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ShareRepository shareRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;


    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OTPVerificationRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MailService mailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$");

    @Override
    public UserResponse getProfile(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(ProfileRequest request, String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String oldName = user.getName();
        user.setName(request.getName().trim());
        userRepository.save(user);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(user)
                .action("UPDATE_PROFILE: " + oldName + " -> " + user.getName())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        // Notify User
        Notification userNotif = Notification.builder()
                .user(user)
                .message("Your profile details were updated successfully.")
                .type("USER")
                .build();
        notificationRepository.save(userNotif);

        return mapToResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New passwords do not match");
        }

        if (!PASSWORD_PATTERN.matcher(request.getNewPassword()).matches()) {
            throw new BadRequestException("Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@#$%^&+=!)");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        if (user.isFirstLogin()) {
            user.setFirstLogin(false);
            user.setEmailVerified(true);
            user.setActive(true);
        }
        userRepository.save(user);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(user)
                .action("PASSWORD_CHANGE")
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        // Notify User
        Notification userNotif = Notification.builder()
                .user(user)
                .message("Your account password has been changed.")
                .type("USER")
                .build();
        notificationRepository.save(userNotif);

        mailService.sendNotificationEmail(user.getEmail(), "SmartDocs - Password Changed",
                "Hello " + user.getName() + ",\n\nYour account password has been changed successfully. If you did not initiate this change, please reset your password or contact the system administrator.");
    }

    @Override
    public List<UserResponse> getAllUsers(String query, int page, int size, String adminEmail) {
        User admin = userRepository.findByEmailAndDeletedAtIsNull(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<User> users;
        
        Workspace workspace = admin.getWorkspace();
        if (workspace == null) {
            if (query != null && !query.trim().isEmpty()) {
                users = userRepository.searchUsers(query.trim(), pageable);
            } else {
                users = userRepository.findAllByDeletedAtIsNull(pageable);
            }
        } else {
            if (query != null && !query.trim().isEmpty()) {
                users = userRepository.searchUsersInWorkspace(workspace, query.trim(), pageable);
            } else {
                users = userRepository.findAllByWorkspaceAndDeletedAtIsNull(workspace, pageable);
            }
        }
        
        return users.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse createEmployee(InviteUserRequest request, String adminEmail) {
        User admin = userRepository.findByEmailAndDeletedAtIsNull(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getRole() != Role.ADMIN && admin.getRole() != Role.SUPER_ADMIN) {
            throw new BadRequestException("Access denied: Only administrators can invite users");
        }

        Workspace workspace = admin.getWorkspace();
        if (workspace != null && "PERSONAL".equals(workspace.getWorkspaceType())) {
            throw new BadRequestException("Personal workspaces cannot have employees");
        }

        if (userRepository.findByEmail(request.getEmail().trim().toLowerCase()).isPresent()) {
            throw new BadRequestException("Email address is already in use");
        }

        Role assignedRole;
        try {
            assignedRole = Role.valueOf(request.getRole().trim().toUpperCase());
        } catch (Exception e) {
            assignedRole = Role.EMPLOYEE;
        }

        // Generate temporary password
        String tempPassword = generateTempPassword();

        User employee = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(tempPassword))
                .role(assignedRole)
                .workspace(workspace)
                .isEmailVerified(false)
                .isActive(true)
                .firstLogin(true)
                .build();

        userRepository.save(employee);

        AuditLog audit = AuditLog.builder()
                .user(admin)
                .action("INVITE_USER: " + employee.getEmail() + " as " + assignedRole.name())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        // Send activation email
        mailService.sendInvitationEmail(
                employee.getEmail(),
                employee.getName(),
                workspace != null ? workspace.getName() : "SmartDocs",
                assignedRole.name(),
                tempPassword
        );

        Notification empNotif = Notification.builder()
                .user(employee)
                .message("Your account has been invited by the administrator of " + (workspace != null ? workspace.getName() : "the system") + ".")
                .type("USER")
                .build();
        notificationRepository.save(empNotif);

        return mapToResponse(employee);
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(Long userId, String roleStr, String updaterEmail) {
        User updater = userRepository.findByEmailAndDeletedAtIsNull(updaterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Updater not found"));

        if (updater.getRole() != Role.SUPER_ADMIN && updater.getRole() != Role.ADMIN) {
            throw new BadRequestException("Access denied: Only administrators can modify roles");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (target.getEmail().equals(updaterEmail)) {
            throw new BadRequestException("You cannot change your own role");
        }

        Role newRole;
        try {
            newRole = Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + roleStr);
        }

        // Limit ADMIN from escalating to SUPER_ADMIN
        if (updater.getRole() == Role.ADMIN && newRole == Role.SUPER_ADMIN) {
            throw new BadRequestException("Access denied: Admin cannot assign SUPER_ADMIN role");
        }

        Role oldRole = target.getRole();
        target.setRole(newRole);
        userRepository.save(target);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(updater)
                .action("CHANGE_ROLE: " + target.getEmail() + " (" + oldRole + " -> " + newRole + ")")
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        // Notification for user
        Notification userNotif = Notification.builder()
                .user(target)
                .message("Your user account role has been updated from " + oldRole + " to " + newRole)
                .type("USER")
                .build();
        notificationRepository.save(userNotif);

        return mapToResponse(target);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long userId, boolean active, String updaterEmail) {
        User updater = userRepository.findByEmailAndDeletedAtIsNull(updaterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Updater not found"));

        if (updater.getRole() != Role.SUPER_ADMIN && updater.getRole() != Role.ADMIN) {
            throw new BadRequestException("Access denied: Only administrators can modify account status");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (target.getEmail().equals(updaterEmail)) {
            throw new BadRequestException("You cannot activate/deactivate your own account");
        }

        target.setActive(active);
        userRepository.save(target);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(updater)
                .action("STATUS_CHANGE: " + target.getEmail() + " (Active=" + active + ")")
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        // Notification for user
        Notification userNotif = Notification.builder()
                .user(target)
                .message("Your account status was set to " + (active ? "ACTIVE" : "SUSPENDED"))
                .type("USER")
                .build();
        notificationRepository.save(userNotif);

        return mapToResponse(target);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, String updaterEmail) {
        User updater = userRepository.findByEmailAndDeletedAtIsNull(updaterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Updater not found"));

        if (updater.getRole() != Role.SUPER_ADMIN && updater.getRole() != Role.ADMIN) {
            throw new BadRequestException("Access denied: Only administrators can delete users");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (target.getEmail().equals(updaterEmail)) {
            throw new BadRequestException("You cannot delete your own account");
        }

        // 1. Find all documents owned by user, delete physical files of all versions
        List<Document> docs = documentRepository.findAllByUserAndIsDeletedFalse(target);
        docs.addAll(documentRepository.findByUserAndIsDeletedTrue(target));
        for (Document doc : docs) {
            List<DocumentVersion> versions = versionRepository.findAllByDocument(doc);
            for (DocumentVersion ver : versions) {
                deletePhysicalFile(ver.getFilePath());
            }
            versionRepository.deleteAll(versions);

            List<Share> shares = shareRepository.findAllByDocument(doc);
            shareRepository.deleteAll(shares);

            auditLogRepository.nullifyDocumentReferences(doc);
            documentRepository.delete(doc);
        }

        // 2. Delete folders owned by user
        List<Folder> folders = folderRepository.findByUserAndDeletedAtIsNull(target);
        folderRepository.deleteAll(folders);

        // 3. Clear other references
        notificationRepository.deleteAllByUser(target);
        loginHistoryRepository.deleteAllByUser(target);
        auditLogRepository.deleteAllByUser(target);
        otpRepository.deleteByEmail(target.getEmail());

        // 4. Hard delete user record safely
        userRepository.delete(target);

        // Audit Log for Admin
        AuditLog audit = AuditLog.builder()
                .user(updater)
                .action("DELETE_USER: " + target.getEmail())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);
    }

    @Override
    public List<Map<String, Object>> getNotifications(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String userRole = user.getRole().name();
        String queryType = userRole.equals("SUPER_ADMIN") ? "ADMIN" : userRole;
        List<Notification> notifs = notificationRepository.findNotificationsForUser(user, queryType, user.getWorkspace());

        return notifs.stream().map(n -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", n.getId());
            map.put("message", n.getMessage());
            map.put("isRead", n.isRead());
            map.put("type", n.getType());
            map.put("createdAt", n.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markNotificationAsRead(Long notifId, String email) {
        Notification notification = notificationRepository.findById(notifId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    public long getUnreadNotificationCount(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String userRole = user.getRole().name();
        String queryType = userRole.equals("SUPER_ADMIN") ? "ADMIN" : userRole;
        return notificationRepository.countUnreadNotificationsForUser(user, queryType, user.getWorkspace());
    }

    private void deletePhysicalFile(String filePathStr) {
        if (filePathStr == null || filePathStr.isEmpty()) return;
        try {
            File file = new File(filePathStr);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    System.out.println("Physical file deleted: " + filePathStr);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to delete physical file " + filePathStr + ": " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void resendInvitation(Long userId, String adminEmail) {
        User admin = userRepository.findByEmailAndDeletedAtIsNull(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getRole() != Role.ADMIN && admin.getRole() != Role.SUPER_ADMIN) {
            throw new BadRequestException("Access denied: Only administrators can resend invitations");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("User has already activated/verified their account");
        }

        String tempPassword = generateTempPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setFirstLogin(true);
        user.setEmailVerified(false);
        user.setActive(true);
        userRepository.save(user);

        AuditLog audit = AuditLog.builder()
                .user(admin)
                .action("RESEND_INVITATION: " + user.getEmail())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        mailService.sendInvitationEmail(
                user.getEmail(),
                user.getName(),
                user.getWorkspace() != null ? user.getWorkspace().getName() : "SmartDocs",
                user.getRole().name(),
                tempPassword
        );
    }

    @Override
    @Transactional
    public void resetPasswordByAdmin(Long userId, String adminEmail) {
        User admin = userRepository.findByEmailAndDeletedAtIsNull(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getRole() != Role.ADMIN && admin.getRole() != Role.SUPER_ADMIN) {
            throw new BadRequestException("Access denied: Only administrators can reset user passwords");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String tempPassword = generateTempPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setFirstLogin(true);
        user.setEmailVerified(false);
        user.setActive(true);
        userRepository.save(user);

        AuditLog audit = AuditLog.builder()
                .user(admin)
                .action("RESET_PASSWORD_ADMIN: " + user.getEmail())
                .ipAddress("127.0.0.1")
                .browser("Browser")
                .os("System")
                .device("Desktop")
                .build();
        auditLogRepository.save(audit);

        mailService.sendInvitationEmail(
                user.getEmail(),
                user.getName(),
                user.getWorkspace() != null ? user.getWorkspace().getName() : "SmartDocs",
                user.getRole().name(),
                tempPassword
        );
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .emailVerified(user.isEmailVerified())
                .active(user.isActive())
                .firstLogin(user.isFirstLogin())
                .createdAt(user.getCreatedAt())
                .workspaceId(user.getWorkspace() != null ? user.getWorkspace().getId() : null)
                .workspaceName(user.getWorkspace() != null ? user.getWorkspace().getName() : null)
                .workspaceType(user.getWorkspace() != null ? user.getWorkspace().getWorkspaceType() : null)
                .build();
    }

    private String getActivationLink(String token) {
        return frontendUrl + "/activate-account?token=" + token;
    }

    private String generateTempPassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "@#$%^&+=!";
        String all = upper + lower + digits + special;
        java.security.SecureRandom random = new java.security.SecureRandom();
        
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(special.charAt(random.nextInt(special.length())));
        for (int i = 0; i < 4; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        return sb.toString();
    }
}
