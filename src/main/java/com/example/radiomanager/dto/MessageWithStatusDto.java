// src/main/java/com/example/radiomanager/dto/MessageWithStatusDto.java
package com.example.radiomanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageWithStatusDto {
    private Long id;
    private String userLogin;
    private String userFullName;
    private String content;
    private LocalDateTime sentAt;
    private String status;
}