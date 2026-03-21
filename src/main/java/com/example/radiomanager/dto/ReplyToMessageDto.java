package com.example.radiomanager.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplyToMessageDto {
    private Long messageId;
    private String reply;
}
