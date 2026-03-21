package com.example.radiomanager.controller;

import com.example.radiomanager.dto.*;
import com.example.radiomanager.model.User;
import com.example.radiomanager.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserService userService;


    @GetMapping("/users")
    public ResponseEntity<List<UserAdminResponseDto>> getUsers(
            @RequestParam(required = false) String login,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {

        log.info("Admin get users - login: {}, fullName: {}, role: {}, dateFrom: {}, dateTo: {}",
                login, fullName, role, dateFrom, dateTo);

        List<UserAdminResponseDto> users = userService.getAllActiveUsers(login, fullName, role, dateFrom, dateTo);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserAdminResponseDto> getUserById(@PathVariable Long id) {
        log.info("Admin get user by ID: {}", id);
        User user = userService.getActiveUserById(id);
        return ResponseEntity.ok(new UserAdminResponseDto(
                user.getId(),
                user.getLogin(),
                user.getFullName(),
                user.getRoles(),
                user.getRegistrationDate(),
                user.isDeleted()
        ));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> updateUser(
            @PathVariable Long id,
            @RequestBody UserUpdateDto updateDto) {

        log.info("Admin updating user: {}", id);

        Map<String, String> response = new HashMap<>();

        try {
            userService.updateUser(id, updateDto);
            response.put("status", "success");
            response.put("message", "Пользователь успешно обновлен");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error updating user: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        log.info("Admin deleting user: {}", id);

        Map<String, String> response = new HashMap<>();

        try {
            userService.softDeleteUser(id);
            response.put("status", "success");
            response.put("message", "Пользователь успешно удален");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error deleting user: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/users/{id}/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable Long id,
            @RequestBody ChangePasswordDto passwordDto) {

        log.info("Admin changing password for user: {}", id);

        Map<String, String> response = new HashMap<>();
        String result = userService.changePassword(id, passwordDto);

        if ("SUCCESS".equals(result)) {
            response.put("status", "success");
            response.put("message", "Пароль успешно изменен");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", result);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/users/{id}/assign-roles")
    public ResponseEntity<Map<String, String>> assignRoles(
            @PathVariable Long id,
            @RequestBody AssignRolesDto rolesDto) {

        log.info("Admin assigning roles for user: {}, roles: {}", id, rolesDto.getRoles());

        Map<String, String> response = new HashMap<>();

        try {
            userService.assignRoles(id, rolesDto.getRoles());
            response.put("status", "success");
            response.put("message", "Роли успешно назначены");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error assigning roles: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
