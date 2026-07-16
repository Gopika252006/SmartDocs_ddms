package com.smartdocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class SmartDocsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartDocsApplication.class, args);
    }
}
