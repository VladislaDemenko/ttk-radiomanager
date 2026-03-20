package com.example.radiomanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationDto {
    private String login;
    private String fullName;
    private String password;
    private String confirmPassword;
}
