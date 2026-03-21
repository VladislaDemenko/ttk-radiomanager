package com.example.radiomanager.controller;

import com.example.radiomanager.dto.LoginRequestDto;
import com.example.radiomanager.dto.UserRegistrationDto;
import com.example.radiomanager.dto.UserResponseDto;
import com.example.radiomanager.model.User;
import com.example.radiomanager.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody UserRegistrationDto registrationDto) {
        log.info("Received registration request for login: {}", registrationDto.getLogin());

        String result = userService.registerUser(registrationDto);
        Map<String, String> response = new HashMap<>();

        if ("SUCCESS".equals(result)) {
            log.info("Registration successful for login: {}", registrationDto.getLogin());
            response.put("status", "success");
            response.put("message", "Регистрация успешно завершена!");
            return ResponseEntity.ok(response);
        } else {
            log.warn("Registration failed for login: {} - Reason: {}", registrationDto.getLogin(), result);
            response.put("status", "error");
            response.put("message", result);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getLogin());

        try {
            User user = userService.authenticate(loginRequest.getLogin(), loginRequest.getPassword());

            UserResponseDto response = new UserResponseDto(
                    user.getId(),
                    user.getLogin(),
                    user.getFullName(),
                    user.getRoles()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(401).body(error);
        }
    }

    @GetMapping("/check-login")
    public ResponseEntity<Map<String, Boolean>> checkLogin(@RequestParam String login) {
        log.debug("Checking login availability: {}", login);
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", userService.checkLoginExists(login));
        return ResponseEntity.ok(response);
    }
}