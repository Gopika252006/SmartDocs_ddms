package com.smartdocs.util;

import java.security.SecureRandom;

public class OTPGenerator {
    private static final SecureRandom random = new SecureRandom();

    public static String generateOTP() {
        int num = 100000 + random.nextInt(900000);
        return String.valueOf(num);
    }
}
