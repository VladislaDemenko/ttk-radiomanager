package com.example.radiomanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminResponseDto {
    private Long id;
    private String login;
    private String fullName;
    private Set<String> roles;
    private LocalDateTime registrationDate;
    private boolean deleted;
}