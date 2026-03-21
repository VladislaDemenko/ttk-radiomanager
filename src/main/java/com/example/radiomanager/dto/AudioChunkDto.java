package com.example.radiomanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioChunkDto {
    private int chunkNumber;
    private String data;
    private boolean isLast;
    private String trackName;
}
