package com.smartdocs.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<?> home() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "SmartDocs Enterprise Digital Document Management System Backend API is running successfully.");
        return ResponseEntity.ok(response);
    }
}
