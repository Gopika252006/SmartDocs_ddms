package com.smartdocs.util;

import jakarta.servlet.http.HttpServletRequest;

public class ClientInfoParser {

    public static String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        // If multiple IPs are forwarded, extract the first one
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        return ipAddress;
    }

    public static String getBrowser(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Browser";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg")) {
            return "Microsoft Edge";
        } else if (ua.contains("chrome") && !ua.contains("opr")) {
            return "Google Chrome";
        } else if (ua.contains("safari") && !ua.contains("chrome")) {
            return "Apple Safari";
        } else if (ua.contains("firefox")) {
            return "Mozilla Firefox";
        } else if (ua.contains("opr") || ua.contains("opera")) {
            return "Opera";
        } else if (ua.contains("msie") || ua.contains("trident")) {
            return "Internet Explorer";
        }
        return "Unknown Browser";
    }

    public static String getOs(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown OS";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) {
            return "Windows";
        } else if (ua.contains("macintosh") || ua.contains("mac os x")) {
            return "macOS";
        } else if (ua.contains("linux")) {
            return "Linux";
        } else if (ua.contains("android")) {
            return "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad")) {
            return "iOS";
        }
        return "Unknown OS";
    }

    public static String getDevice(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Desktop";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone") || ua.contains("ipad")) {
            return "Mobile";
        }
        return "Desktop";
    }
}
