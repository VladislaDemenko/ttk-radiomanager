// src/main/java/com/example/radiomanager/service/UserService.java (дополнение)
package com.example.radiomanager.service;

import com.example.radiomanager.dto.UserRegistrationDto;
import com.example.radiomanager.model.User;
import com.example.radiomanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String registerUser(UserRegistrationDto registrationDto) {
        if (userRepository.existsByLogin(registrationDto.getLogin())) {
            return "Пользователь с таким логином уже существует";
        }

        if (!registrationDto.getPassword().equals(registrationDto.getConfirmPassword())) {
            return "Пароли не совпадают";
        }

        // Хеширование пароля
        String hashedPassword = passwordEncoder.encode(registrationDto.getPassword());

        User user = new User(
                registrationDto.getLogin(),
                registrationDto.getFullName(),
                hashedPassword,
                LocalDateTime.now()
        );

        userRepository.save(user);
        return "SUCCESS";
    }

    public User authenticate(String login, String password) {
        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Неверный логин или пароль"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Неверный логин или пароль");
        }

        return user;
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    public boolean checkLoginExists(String login) {
        return userRepository.existsByLogin(login);
    }
}