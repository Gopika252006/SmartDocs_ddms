package com.smartdocs.service;

import com.smartdocs.dto.*;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    String register(RegisterRequest request);
    String verifyEmail(VerifyOtpRequest request);
    JwtResponse login(LoginRequest request, HttpServletRequest httpRequest);
    String forgotPassword(ForgotPasswordRequest request);
    String resetPassword(ResetPasswordRequest request);
}
