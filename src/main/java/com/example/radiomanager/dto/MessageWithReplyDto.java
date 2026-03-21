package com.example.radiomanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageWithReplyDto {
    private Long id;
    private Long userId;
    private String userLogin;
    private String userFullName;
    private String content;
    private LocalDateTime sentAt;
    private boolean isRead;
    private String reply;
    private LocalDateTime repliedAt;
    private String repliedBy;
}
