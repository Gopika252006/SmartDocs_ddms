package com.smartdocs.service;

import com.smartdocs.entity.OTPVerification;
import com.smartdocs.exception.BadRequestException;
import com.smartdocs.repository.OTPVerificationRepository;
import com.smartdocs.util.OTPGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OTPVerificationServiceImpl implements OTPVerificationService {

    @Autowired
    private OTPVerificationRepository otpRepository;

    @Autowired
    private MailService mailService;

    @Override
    @Transactional
    public OTPVerification generateAndSendOTP(String email, String purpose) {
        LocalDateTime now = LocalDateTime.now();
        Optional<OTPVerification> existingOtpOpt = otpRepository.findByEmailAndPurpose(email, purpose);

        OTPVerification otpVerification;
        String newOtpCode = OTPGenerator.generateOTP();

        if (existingOtpOpt.isPresent()) {
            otpVerification = existingOtpOpt.get();

            // Enforce resend cooldown (2 minutes)
            if (otpVerification.getLastResendAt() != null &&
                    otpVerification.getLastResendAt().plusMinutes(2).isAfter(now)) {
                throw new BadRequestException("Please wait 2 minutes before requesting a new OTP");
            }

            // Enforce maximum resend attempts (3 attempts)
            if (otpVerification.getResendAttempts() >= 3) {
                // Check if we can unblock (after 30 minutes)
                if (otpVerification.getLastResendAt() != null &&
                        otpVerification.getLastResendAt().plusMinutes(30).isAfter(now)) {
                    throw new BadRequestException("OTP resend attempts exceeded. Please try again after 30 minutes.");
                } else {
                    // Reset attempts
                    otpVerification.setResendAttempts(0);
                }
            }

            // Update with new OTP
            otpVerification.setOtpCode(newOtpCode);
            otpVerification.setExpiresAt(now.plusMinutes(5));
            otpVerification.setResendAttempts(otpVerification.getResendAttempts() + 1);
            otpVerification.setLastResendAt(now);
        } else {
            // First time requesting OTP
            otpVerification = OTPVerification.builder()
                    .email(email)
                    .otpCode(newOtpCode)
                    .purpose(purpose)
                    .expiresAt(now.plusMinutes(5))
                    .resendAttempts(0)
                    .lastResendAt(now)
                    .build();
        }

        otpRepository.save(otpVerification);

        // Send Email
        mailService.sendOtpEmail(email, newOtpCode, purpose);

        return otpVerification;
    }

    @Override
    @Transactional
    public boolean verifyOTP(String email, String code, String purpose) {
        LocalDateTime now = LocalDateTime.now();
        OTPVerification otpVerification = otpRepository.findByEmailAndPurpose(email, purpose)
                .orElseThrow(() -> new BadRequestException("No OTP requested for this email"));

        if (otpVerification.getExpiresAt().isBefore(now)) {
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        if (!otpVerification.getOtpCode().equals(code)) {
            throw new BadRequestException("Invalid OTP code");
        }

        // Successfully verified, delete the OTP record immediately
        otpRepository.delete(otpVerification);
        return true;
    }

    @Override
    @Transactional
    public void deleteOTP(String email) {
        otpRepository.deleteByEmail(email);
    }
}
