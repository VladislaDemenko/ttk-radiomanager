package com.example.radiomanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioFileDto {
    private Long id;
    private String fileName;
    private String originalName;
    private long fileSize;
    private boolean isVoiceNote;
    private String uploadedAt;
}