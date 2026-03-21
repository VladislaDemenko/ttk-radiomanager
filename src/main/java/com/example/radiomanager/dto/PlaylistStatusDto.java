package com.example.radiomanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistStatusDto {
    private int currentTrackIndex;
    private int totalTracks;
    private boolean looping;
    private boolean shuffling;
    private String statusText;
}