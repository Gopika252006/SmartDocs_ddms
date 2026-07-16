package com.smartdocs.service;

import com.smartdocs.dto.*;
import com.smartdocs.entity.*;
import com.smartdocs.exception.BadRequestException;
import com.smartdocs.exception.ResourceNotFoundException;
import com.smartdocs.repository.AuditLogRepository;
import com.smartdocs.repository.LoginHistoryRepository;
import com.smartdocs.repository.NotificationRepository;
import com.smartdocs.repository.UserRepository;
import com.smartdocs.repository.WorkspaceRepository;
import com.smartdocs.repository.CategoryRepository;
import com.smartdocs.security.JwtUtils;
import com.smartdocs.security.UserDetailsImpl;
import com.smartdocs.util.ClientInfoParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OTPVerificationService otpService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MailService mailService;

    // Strong password check: min 8 characters, 1 digit, 1 lowercase, 1 uppercase, 1 special character
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$");

    @Override
    @Transactional
    public String register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("Email address is already in use");
        }

        // Validate strong password
        if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
            throw new BadRequestException("Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@#$%^&+=!)");
        }

        // Create Workspace
        String wsName = request.getWorkspaceName();
        String wsTypeStr = request.getWorkspaceType() != null ? request.getWorkspaceType().toUpperCase() : "PERSONAL";
        
        if (wsName == null || wsName.trim().isEmpty()) {
            if (wsTypeStr.equals("PERSONAL")) {
                wsName = "Personal Workspace - " + request.getName();
            } else {
                wsName = request.getName() + "'s Workspace";
            }
        }
        
        long maxStorageLimit = "PERSONAL".equalsIgnoreCase(wsTypeStr) ? 100L * 1024 * 1024 : 50L * 1024 * 1024 * 1024;
        
        Workspace workspace = Workspace.builder()
                .name(wsName)
                .workspaceType(wsTypeStr)
                .maxStorageLimit(maxStorageLimit)
                .build();
        workspaceRepository.save(workspace);

        Role role = Role.ADMIN; // Workspace Owner
        boolean isVerified = false; // Require email verification

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .workspace(workspace)
                .isEmailVerified(isVerified)
                .isActive(true)
                .build();

        userRepository.save(user);

        // Provision Categories based on Workspace Type
        String[] dynamicCategories;
        switch (wsTypeStr) {
            case "COLLEGE":
                dynamicCategories = new String[]{"Projects", "Assignments", "Certificates"};
                break;
            case "COMPANY":
                dynamicCategories = new String[]{"Invoices", "Finance", "HR", "Contracts"};
                break;
            case "HOSPITAL":
                dynamicCategories = new String[]{"Medical Reports", "Lab Reports", "Insurance"};
                break;
            case "PERSONAL":
                dynamicCategories = new String[]{"Personal Documents"};
                break;
            case "SCHOOL":
                dynamicCategories = new String[]{"Lessons", "Homework", "Reports", "Certificates"};
                break;
            case "GOVERNMENT":
                dynamicCategories = new String[]{"Policies", "Forms", "Legal Docs", "Audits"};
                break;
            case "STARTUP":
                dynamicCategories = new String[]{"Pitch Decks", "Finance", "Product Spec", "Legal"};
                break;
            case "NGO":
                dynamicCategories = new String[]{"Projects", "Donations", "Reports", "Campaigns"};
                break;
            default:
                dynamicCategories = new String[]{"General", "Invoices", "Reports"};
                break;
        }

        for (String catName : dynamicCategories) {
            Category category = Category.builder()
                    .name(catName)
                    .user(user)
                    .workspace(workspace)
                    .build();
            categoryRepository.save(category);
        }

        // System notification & logs
        AuditLog audit = AuditLog.builder()
                .user(user)
                .action("REGISTER")
                .ipAddress("127.0.0.1")
                .browser("System")
                .os("System")
                .device("System")
                .build();
        auditLogRepository.save(audit);

        // Send OTP for email verification
        otpService.generateAndSendOTP(request.getEmail(), "EMAIL_VERIFICATION");

        // Notify admins about new user
        Notification adminNotif = Notification.builder()
                .message("New User Registered: " + user.getName() + " (" + user.getEmail() + ")")
                .type("ADMIN")
                .workspace(user.getWorkspace())
                .build();
        notificationRepository.save(adminNotif);

        return "Registration successful. Please verify your email using the OTP sent.";
    }

    @Override
    @Transactional
    public String verifyEmail(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        if (user.isEmailVerified()) {
            return "Email is already verified";
        }

        otpService.verifyOTP(request.getEmail(), request.getOtpCode(), "EMAIL_VERIFICATION");

        user.setEmailVerified(true);
        userRepository.save(user);

        // Notify User
        Notification userNotif = Notification.builder()
                .user(user)
                .message("Email verified successfully. Welcome to SmartDocs!")
                .type("USER")
                .build();
        notificationRepository.save(userNotif);

        mailService.sendNotificationEmail(user.getEmail(), "SmartDocs - Email Verified",
                "Hello " + user.getName() + ",\n\nYour email has been verified successfully. You can now log into your account.");

        return "Email verified successfully. You can now login.";
    }

    @Override
    @Transactional
    public JwtResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ip = ClientInfoParser.getClientIp(httpRequest);
        String browser = ClientInfoParser.getBrowser(userAgent);
        String os = ClientInfoParser.getOs(userAgent);
        String device = ClientInfoParser.getDevice(userAgent);

        String trimmedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmailAndDeletedAtIsNull(trimmedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isActive()) {
            throw new BadRequestException("Your account is deactivated. Please contact the administrator.");
        }

        if (user.getWorkspace() != null && !user.getWorkspace().isActive()) {
            throw new BadRequestException("Your workspace has been suspended. Please contact the platform owner.");
        }

        if (!user.isEmailVerified() && !user.isFirstLogin()) {
            throw new BadRequestException("Please verify your email before signing in.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(trimmedEmail, request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            // Track login history
            LoginHistory history = LoginHistory.builder()
                    .user(user)
                    .ipAddress(ip)
                    .browser(browser)
                    .os(os)
                    .device(device)
                    .location("Local Network")
                    .build();
            loginHistoryRepository.save(history);

            // Audit Log
            AuditLog audit = AuditLog.builder()
                    .user(user)
                    .action("LOGIN")
                    .ipAddress(ip)
                    .browser(browser)
                    .os(os)
                    .device(device)
                    .build();
            auditLogRepository.save(audit);

            return JwtResponse.builder()
                    .token(jwt)
                    .id(userDetails.getId())
                    .name(userDetails.getName())
                    .email(userDetails.getUsername())
                    .role(user.getRole().name())
                    .workspaceName(user.getWorkspace() != null ? user.getWorkspace().getName() : null)
                    .workspaceType(user.getWorkspace() != null ? user.getWorkspace().getWorkspaceType() : null)
                    .firstLogin(user.isFirstLogin())
                    .build();

        } catch (BadCredentialsException e) {
            // Log failed login event in audits
            AuditLog audit = AuditLog.builder()
                    .user(user)
                    .action("FAILED_LOGIN")
                    .ipAddress(ip)
                    .browser(browser)
                    .os(os)
                    .device(device)
                    .build();
            auditLogRepository.save(audit);
            throw e;
        }
    }

    @Override
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        otpService.generateAndSendOTP(user.getEmail(), "PASSWORD_RESET");
        return "Password reset OTP sent to your email";
    }

    @Override
    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        // Verify OTP
        otpService.verifyOTP(request.getEmail(), request.getOtpCode(), "PASSWORD_RESET");

        // Validate strong password
        if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
            throw new BadRequestException("Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@#$%^&+=!)");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        // Audit Log
        AuditLog audit = AuditLog.builder()
                .user(user)
                .action("PASSWORD_RESET")
                .ipAddress("127.0.0.1")
                .browser("System")
                .os("System")
                .device("System")
                .build();
        auditLogRepository.save(audit);

        // Notify User
        Notification userNotif = Notification.builder()
                .user(user)
                .message("Your account password was reset successfully.")
                .type("USER")
                .build();
        notificationRepository.save(userNotif);

        mailService.sendNotificationEmail(user.getEmail(), "SmartDocs - Password Changed",
                "Hello " + user.getName() + ",\n\nYour account password has been reset successfully. If you did not initiate this change, please contact support immediately.");

        return "Password reset successfully. You can now login.";
    }
}
