package com.smartdocs.service;

import com.smartdocs.entity.OTPVerification;

public interface OTPVerificationService {
    OTPVerification generateAndSendOTP(String email, String purpose);
    boolean verifyOTP(String email, String code, String purpose);
    void deleteOTP(String email);
}
