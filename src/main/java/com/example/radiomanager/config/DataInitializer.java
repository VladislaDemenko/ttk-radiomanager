package com.example.radiomanager.config;

import com.example.radiomanager.model.User;
import com.example.radiomanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("=== DataInitializer started ===");
        log.info("Total users in database: {}", userRepository.count());

        if (userRepository.count() == 0) {
            log.info("No users found. Creating default admin user...");

            try {
                String rawPassword = "admin123";
                String encodedPassword = passwordEncoder.encode(rawPassword);

                User admin = new User(
                        "admin",
                        "Администратор Системы",
                        encodedPassword,
                        LocalDateTime.now()
                );

                admin.setRoles(Set.of("ADMIN", "USER"));

                User savedAdmin = userRepository.save(admin);

                log.info("=== Default admin user created successfully! ===");
                log.info("Login: admin");
                log.info("Password: admin123");
                log.info("Roles: ADMIN, USER");
                log.info("===========================================");

                boolean matches = passwordEncoder.matches(rawPassword, savedAdmin.getPassword());
                log.info("Password verification test: {}", matches ? "SUCCESS" : "FAILED");

            } catch (Exception e) {
                log.error("Error creating admin user: ", e);
            }
        } else {
            log.info("Database already contains {} users.", userRepository.count());
        }
        log.info("DataInitializer finished");
    }
}