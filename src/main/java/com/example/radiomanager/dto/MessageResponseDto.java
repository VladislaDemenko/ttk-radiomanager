package com.example.radiomanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponseDto {
    private Long id;
    private String userLogin;
    private String userFullName;
    private String content;
    private LocalDateTime sentAt;
    private boolean isRead;
}
