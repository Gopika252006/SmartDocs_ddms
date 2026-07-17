package com.smartdocs.config;

import com.smartdocs.entity.Role;
import com.smartdocs.entity.User;
import com.smartdocs.entity.Workspace;
import com.smartdocs.repository.UserRepository;
import com.smartdocs.repository.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Ensure default Workspace exists
        Workspace workspace = workspaceRepository.findByName("SmartDocs Global Workspace")
                .orElseGet(() -> {
                    Workspace ws = Workspace.builder()
                            .name("SmartDocs Global Workspace")
                            .workspaceType("COMPANY")
                            .active(true)
                            .maxStorageLimit(50L * 1024 * 1024 * 1024) // 50 GB
                            .build();
                    return workspaceRepository.save(ws);
                });

        // Ensure default Super Admin exists
        if (userRepository.findByEmail("admin@smartdocs.com").isEmpty()) {
            User superAdmin = User.builder()
                    .name("Super Admin")
                    .email("admin@smartdocs.com")
                    .password(passwordEncoder.encode("Password@123"))
                    .role(Role.SUPER_ADMIN)
                    .workspace(workspace)
                    .isEmailVerified(true)
                    .firstLogin(false)
                    .isActive(true)
                    .build();
            userRepository.save(superAdmin);
            System.out.println("[Database Seeder] Super Admin created: admin@smartdocs.com / Password@123");
        }

        // Ensure default Admin exists
        if (userRepository.findByEmail("praveen@smartdocs.com").isEmpty()) {
            User admin = User.builder()
                    .name("Praveen")
                    .email("praveen@smartdocs.com")
                    .password(passwordEncoder.encode("Password@123"))
                    .role(Role.ADMIN)
                    .workspace(workspace)
                    .isEmailVerified(true)
                    .firstLogin(false)
                    .isActive(true)
                    .build();
            userRepository.save(admin);
            System.out.println("[Database Seeder] Admin created: praveen@smartdocs.com / Password@123");
        }
    }
}
