package com.smartdocs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;
import com.smartdocs.exception.BadRequestException;

@Service
public class MailServiceImpl implements MailService {
    private static final Logger logger = LoggerFactory.getLogger(MailServiceImpl.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void sendOtpEmail(String toEmail, String otp, String purpose) {
        String subject = "SmartDocs - " + (purpose.equals("EMAIL_VERIFICATION") ? "Verify your email" : "Reset your password");
        String messageText = "Hello,\n\n" +
                "Your one-time passcode (OTP) for SmartDocs is: " + otp + "\n" +
                "This code will expire in 5 minutes.\n\n" +
                "If you did not request this, please ignore this email.\n\n" +
                "Regards,\n" +
                "SmartDocs Security Team";

        logger.info("\n==================================================" +
                    "\n[OUTGOING EMAIL SIMULATOR]" +
                    "\nTo: " + toEmail +
                    "\nSubject: " + subject +
                    "\nBody:\n" + messageText +
                    "\n==================================================");

        // Check if SMTP is not configured or uses default placeholders
        if (mailUsername == null || mailUsername.isEmpty() || mailUsername.contains("YOUR_GMAIL_ADDRESS")) {
            logger.warn("SMTP Email service is not configured. (Simulator mode: OTP is printed in logs)");
            return;
        }

        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(messageText);
                mailSender.send(message);
                logger.info("Email sent successfully to {}", toEmail);
            } catch (Exception e) {
                logger.error("SMTP send failed. Reason: {}. (Simulator fallback active)", e.getMessage());
            }
        } else {
            logger.warn("Real SMTP JavaMailSender is not configured. (Simulator mode active)");
        }
    }

    @Override
    public void sendNotificationEmail(String toEmail, String subject, String messageText) {
        logger.info("\n==================================================" +
                    "\n[OUTGOING EMAIL SIMULATOR]" +
                    "\nTo: " + toEmail +
                    "\nSubject: " + subject +
                    "\nBody:\n" + messageText +
                    "\n==================================================");

        // Silent check for non-critical notification emails so login/change flows don't crash if SMTP settings are not set
        if (mailUsername == null || mailUsername.isEmpty() || mailUsername.contains("YOUR_GMAIL_ADDRESS")) {
            logger.warn("SMTP email notification not sent (SMTP not configured). To: {}, Subject: {}", toEmail, subject);
            return;
        }

        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(messageText);
                mailSender.send(message);
                logger.info("Notification email sent successfully to {}", toEmail);
            } catch (Exception e) {
                logger.warn("SMTP notification send failed. Reason: {}", e.getMessage());
            }
        }
    }

    @Override
    public void sendInvitationEmail(String toEmail, String name, String workspaceName, String role, String tempPassword) {
        String subject = "Welcome to SmartDocs Enterprise";
        String loginLink = frontendUrl + "/login";
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <style>\n" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f6f9; color: #333; margin: 0; padding: 20px; }\n" +
                "        .card { max-width: 600px; margin: 0 auto; background: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); border-top: 5px solid #1a73e8; }\n" +
                "        .logo { font-size: 26px; font-weight: bold; color: #1a73e8; margin-bottom: 25px; display: flex; align-items: center; gap: 8px; }\n" +
                "        .welcome { font-size: 20px; font-weight: 600; margin-bottom: 15px; color: #202124; }\n" +
                "        .details { background-color: #f8f9fa; padding: 18px; border-radius: 8px; margin-bottom: 25px; border-left: 4px solid #1a73e8; }\n" +
                "        .details p { margin: 6px 0; font-size: 14px; color: #4a4a4a; }\n" +
                "        .btn-container { text-align: center; margin: 30px 0; }\n" +
                "        .btn { background-color: #1a73e8; color: #ffffff !important; padding: 12px 32px; text-decoration: none; border-radius: 24px; font-weight: bold; display: inline-block; box-shadow: 0 4px 6px rgba(26, 115, 232, 0.15); }\n" +
                "        .btn:hover { background-color: #1557b0; }\n" +
                "        .warning-box { background-color: #fef7e0; border-left: 4px solid #f4b400; padding: 15px; border-radius: 8px; margin-bottom: 25px; }\n" +
                "        .warning-box p { margin: 0; font-size: 14px; color: #b06000; line-height: 1.4; }\n" +
                "        .footer { font-size: 12px; color: #888; margin-top: 30px; text-align: center; line-height: 1.5; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <div class=\"logo\">📄 SmartDocs</div>\n" +
                "        <div class=\"welcome\">Hello " + name + ",</div>\n" +
                "        <p>Welcome to SmartDocs – Enterprise AI Digital Document Management System.</p>\n" +
                "        <p>Your account has been created successfully.</p>\n" +
                "        \n" +
                "        <div class=\"details\">\n" +
                "            <p><strong>Workspace:</strong> " + workspaceName + "</p>\n" +
                "            <p><strong>Role:</strong> " + role + "</p>\n" +
                "            <p><strong>Email:</strong> " + toEmail + "</p>\n" +
                "            <p><strong>Temporary Password:</strong> <code style=\"font-size: 16px; font-weight: bold; background: #e8f0fe; padding: 2px 6px; border-radius: 4px; color: #1a73e8;\">" + tempPassword + "</code></p>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"warning-box\">\n" +
                "            <p><strong>IMPORTANT:</strong> This is a temporary password generated by SmartDocs. For your security, you must change this password immediately after your first login.</p>\n" +
                "        </div>\n" +
                "        \n" +
                "        <p>Click the button below to log in:</p>\n" +
                "        \n" +
                "        <div class=\"btn-container\">\n" +
                "            <a href=\"" + loginLink + "\" class=\"btn\">Go to SmartDocs Login</a>\n" +
                "        </div>\n" +
                "        \n" +
                "        <p>After logging in with your temporary password, you will automatically be redirected to create your own password.</p>\n" +
                "        \n" +
                "        <div class=\"footer\">\n" +
                "            Thank you,<br>\n" +
                "            <strong>SmartDocs Team</strong>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        logger.info("\n==================================================" +
                    "\n[OUTGOING EMAIL SIMULATOR]" +
                    "\nTo: " + toEmail +
                    "\nSubject: " + subject +
                    "\nTemporary Password: " + tempPassword +
                    "\nLogin Link: " + loginLink +
                    "\nBody:\n" + htmlContent +
                    "\n==================================================");

        if (mailUsername == null || mailUsername.isEmpty() || mailUsername.contains("YOUR_GMAIL_ADDRESS")) {
            logger.warn("SMTP invitation email not sent (SMTP not configured). To: {}, Subject: {}", toEmail, subject);
            return;
        }

        if (mailSender != null) {
            new Thread(() -> {
                try {
                    MimeMessage mimeMessage = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                    helper.setTo(toEmail);
                    helper.setSubject(subject);
                    helper.setText(htmlContent, true);
                    mailSender.send(mimeMessage);
                    logger.info("Invitation HTML email sent successfully to {}", toEmail);
                } catch (Exception e) {
                    logger.warn("SMTP HTML invitation send failed. Reason: {}", e.getMessage());
                }
            }).start();
        }
    }
}
