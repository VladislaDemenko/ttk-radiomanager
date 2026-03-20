package com.example.radiomanager.service;

import com.example.radiomanager.dto.UserRegistrationDto;
import com.example.radiomanager.model.User;
import com.example.radiomanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    public String registerUser(UserRegistrationDto registrationDto) {
        if (userRepository.existsByLogin(registrationDto.getLogin())) {
            return "Пользователь с таким логином уже существует";
        }

        if (!registrationDto.getPassword().equals(registrationDto.getConfirmPassword())) {
            return "Пароли не совпадают";
        }

        User user = new User(
                registrationDto.getLogin(),
                registrationDto.getFullName(),
                registrationDto.getPassword(), // В реальном приложении нужно хэшировать пароль!
                LocalDateTime.now()
        );

        userRepository.save(user);
        return "SUCCESS";
    }

    public User authenticate(String login, String password) {
        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Неверный логин или пароль"));

        if (!user.getPassword().equals(password)) {
            throw new RuntimeException("Неверный логин или пароль");
        }

        return user;
    }

    public boolean checkLoginExists(String login) {
        return userRepository.existsByLogin(login);
    }
}