package com.example.radiomanager.config;

import com.example.radiomanager.model.User;
import com.example.radiomanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("=== DataInitializer started ===");

        createPlaylistTables();

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

            } catch (Exception e) {
                log.error("Error creating admin user: ", e);
            }
        } else {
            log.info("Database already contains {} users.", userRepository.count());
        }
        log.info("DataInitializer finished");
    }

    private void createPlaylistTables() {
        try {
            jdbcTemplate.execute("SELECT COUNT(*) FROM playlists");
            log.info("Table 'playlists' already exists");
        } catch (Exception e) {
            log.info("Creating 'playlists' table...");
            try {
                jdbcTemplate.execute("""
                    CREATE TABLE playlists (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        is_active BOOLEAN DEFAULT FALSE,
                        is_looping BOOLEAN DEFAULT FALSE,
                        is_shuffling BOOLEAN DEFAULT FALSE,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                """);
                log.info("Table 'playlists' created successfully");
            } catch (Exception ex) {
                log.error("Error creating 'playlists' table: {}", ex.getMessage());
            }
        }

        try {
            jdbcTemplate.execute("SELECT COUNT(*) FROM playlist_items");
            log.info("Table 'playlist_items' already exists");
        } catch (Exception e) {
            log.info("Creating 'playlist_items' table...");
            try {
                jdbcTemplate.execute("""
                    CREATE TABLE playlist_items (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        playlist_id BIGINT NOT NULL,
                        audio_file_id BIGINT NOT NULL,
                        position INT NOT NULL,
                        FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                        FOREIGN KEY (audio_file_id) REFERENCES audio_files(id) ON DELETE CASCADE
                    )
                """);
                log.info("Table 'playlist_items' created successfully");
            } catch (Exception ex) {
                log.error("Error creating 'playlist_items' table: {}", ex.getMessage());
            }
        }
    }
}