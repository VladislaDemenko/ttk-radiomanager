package com.example.radiomanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastInfoDto {
    private boolean isLive;
    private String currentTrack;
    private String currentArtist;
    private int listenersCount;
    private LocalDateTime startedAt;
}