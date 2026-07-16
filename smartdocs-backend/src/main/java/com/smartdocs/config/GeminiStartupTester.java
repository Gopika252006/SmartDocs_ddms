package com.smartdocs.config;

import com.smartdocs.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class GeminiStartupTester implements CommandLineRunner {

    @Autowired
    private Environment env;

    @Autowired
    private AIService aiService;

    @Override
    public void run(String... args) throws Exception {
        String selectedModel = aiService.getSelectedModel();
        String apiKey = env.getProperty("gemini.api.key", "");
        String maskedKey = apiKey.length() > 4 ? "********" + apiKey.substring(apiKey.length() - 4) : "****";

        System.out.println("----------------------------------------");
        System.out.println("Active Gemini API Version:");
        System.out.println("v1beta");
        System.out.println("Active Endpoint:");
        System.out.println("generateContent");
        System.out.println("Selected Model:");
        System.out.println(selectedModel);
        System.out.println("----------------------------------------");

        System.out.println("[Startup] Performing connection test to Gemini...");
        try {
            boolean success = aiService.testConnection();
            if (success) {
                System.out.println("Connection Test Result: SUCCESS");
                System.out.println("✓ Active Model: " + selectedModel);
                System.out.println("✓ Active API Key Loaded: " + maskedKey);
                System.out.println("✓ Gemini Connection Successful");
                System.out.println("✓ HTTP 200 Received");
                System.out.println("----------------------------------------");
            } else {
                System.err.println("Connection Test Result: FAILED");
            }
        } catch (Exception e) {
            System.err.println("Connection Test Result: FAILED (Exception: " + e.getMessage() + ")");
        }
    }
}
