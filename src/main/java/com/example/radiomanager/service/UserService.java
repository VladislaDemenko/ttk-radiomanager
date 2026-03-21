package com.example.radiomanager.service;

import com.example.radiomanager.dto.*;
import com.example.radiomanager.model.User;
import com.example.radiomanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final Pattern LOGIN_PATTERN = Pattern.compile("^[A-Za-z]+$");
    private static final Pattern FULL_NAME_PATTERN = Pattern.compile("^[А-Яа-яЁё\\s-]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9!@#$%^&*()_\\-+=\\[\\]{};:'\",.<>/?\\\\|`~]+$");

    public String registerUser(UserRegistrationDto registrationDto) {
        log.info("Registering user: {}", registrationDto.getLogin());

        if (userRepository.existsByLogin(registrationDto.getLogin())) {
            return "Пользователь с таким логином уже существует";
        }

        if (!registrationDto.getPassword().equals(registrationDto.getConfirmPassword())) {
            return "Пароли не совпадают";
        }

        if (!validateLogin(registrationDto.getLogin())) {
            return "Логин должен содержать только латинские буквы";
        }

        if (!validateFullName(registrationDto.getFullName())) {
            return "ФИО должно содержать только русские буквы, пробелы или дефис";
        }

        if (!validatePassword(registrationDto.getPassword())) {
            return "Пароль может содержать только латинские буквы, цифры и символы";
        }

        String hashedPassword = passwordEncoder.encode(registrationDto.getPassword());

        User user = new User(
                registrationDto.getLogin(),
                registrationDto.getFullName(),
                hashedPassword,
                LocalDateTime.now()
        );

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getLogin());
        return "SUCCESS";
    }

    public User authenticate(String login, String password) {
        log.info("Authenticating user: {}", login);

        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Неверный логин или пароль"));

        if (user.isDeleted()) {
            throw new RuntimeException("Пользователь удален");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Неверный логин или пароль");
        }

        log.info("User authenticated successfully: {}", login);
        return user;
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    public User getActiveUserById(Long id) {
        return userRepository.findActiveById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    public boolean checkLoginExists(String login) {
        return userRepository.existsByLogin(login);
    }

    public List<UserAdminResponseDto> getAllActiveUsers(String login, String fullName, String role,
                                                        LocalDateTime dateFrom, LocalDateTime dateTo) {
        log.info("Getting active users with filters - login: {}, fullName: {}, role: {}", login, fullName, role);

        LocalDateTime fromDate = dateFrom != null ? dateFrom.with(LocalTime.MIN) : null;
        LocalDateTime toDate = dateTo != null ? dateTo.with(LocalTime.MAX) : null;

        List<User> users = userRepository.findActiveWithFilters(login, fullName, role, fromDate, toDate);
        log.info("Found {} active users", users.size());

        return users.stream()
                .map(this::mapToAdminDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateUser(Long id, UserUpdateDto updateDto) {
        log.info("Updating user: {}", id);
        User user = getActiveUserById(id);

        if (!user.getLogin().equals(updateDto.getLogin()) &&
                userRepository.existsByLogin(updateDto.getLogin())) {
            throw new RuntimeException("Пользователь с таким логином уже существует");
        }

        if (!validateLogin(updateDto.getLogin())) {
            throw new RuntimeException("Логин должен содержать только латинские буквы");
        }

        if (!validateFullName(updateDto.getFullName())) {
            throw new RuntimeException("ФИО должно содержать только русские буквы, пробелы или дефис");
        }

        user.setLogin(updateDto.getLogin());
        user.setFullName(updateDto.getFullName());
        userRepository.save(user);
        log.info("User updated successfully: {}", id);
    }

    @Transactional
    public void softDeleteUser(Long id) {
        log.info("Soft deleting user: {}", id);
        User user = findById(id);

        if (user.getRoles().contains("ADMIN")) {
            List<User> admins = userRepository.findActiveWithFilters(null, null, "ADMIN", null, null);
            long adminCount = admins.stream()
                    .filter(u -> !u.getId().equals(id))
                    .count();

            if (adminCount == 0) {
                throw new RuntimeException("Нельзя удалить последнего администратора");
            }
        }

        userRepository.softDelete(id);
        log.info("User soft deleted successfully: {}", id);
    }

    @Transactional
    public String changePassword(Long id, ChangePasswordDto passwordDto) {
        log.info("Changing password for user: {}", id);

        if (!passwordDto.getNewPassword().equals(passwordDto.getConfirmPassword())) {
            return "Пароли не совпадают";
        }

        if (!validatePassword(passwordDto.getNewPassword())) {
            return "Пароль может содержать только латинские буквы, цифры и символы";
        }

        User user = getActiveUserById(id);
        user.setPassword(passwordEncoder.encode(passwordDto.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", id);
        return "SUCCESS";
    }

    @Transactional
    public void assignRoles(Long id, Set<String> roles) {
        log.info("Assigning roles for user: {}, roles: {}", id, roles);
        User user = getActiveUserById(id);

        Set<String> validRoles = Set.of("USER", "BROADCASTER", "ADMIN");
        if (!validRoles.containsAll(roles)) {
            throw new RuntimeException("Недопустимые роли. Доступны: USER, BROADCASTER, ADMIN");
        }

        if (user.getRoles().contains("ADMIN") && !roles.contains("ADMIN")) {
            List<User> admins = userRepository.findActiveWithFilters(null, null, "ADMIN", null, null);
            if (admins.size() == 1 && admins.get(0).getId().equals(id)) {
                throw new RuntimeException("Нельзя снять роль ADMIN у последнего администратора");
            }
        }

        user.setRoles(roles);
        userRepository.save(user);
        log.info("Roles assigned successfully for user: {}", id);
    }

    private boolean validateLogin(String login) {
        return LOGIN_PATTERN.matcher(login).matches();
    }

    private boolean validateFullName(String fullName) {
        return FULL_NAME_PATTERN.matcher(fullName).matches();
    }

    private boolean validatePassword(String password) {
        return PASSWORD_PATTERN.matcher(password).matches();
    }

    private UserAdminResponseDto mapToAdminDto(User user) {
        return new UserAdminResponseDto(
                user.getId(),
                user.getLogin(),
                user.getFullName(),
                user.getRoles(),
                user.getRegistrationDate(),
                user.isDeleted()
        );
    }
}