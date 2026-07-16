package com.smartdocs.service;

public interface MailService {
    void sendOtpEmail(String toEmail, String otp, String purpose);
    void sendNotificationEmail(String toEmail, String subject, String messageText);
    void sendInvitationEmail(String toEmail, String name, String workspaceName, String role, String tempPassword);
}
