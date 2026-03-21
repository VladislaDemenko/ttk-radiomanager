package com.example.radiomanager.controller;

import com.example.radiomanager.dto.UserResponseDto;
import com.example.radiomanager.model.User;
import com.example.radiomanager.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {
        log.info("Getting user by ID: {}", id);
        User user = userService.findById(id);
        return ResponseEntity.ok(new UserResponseDto(
                user.getId(),
                user.getLogin(),
                user.getFullName(),
                user.getRoles()
        ));
    }
}
