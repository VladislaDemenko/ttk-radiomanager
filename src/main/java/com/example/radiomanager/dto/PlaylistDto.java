package com.example.radiomanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistDto {
    private Long id;
    private String name;
    private boolean isActive;
    private boolean isLooping;
    private boolean isShuffling;
    private List<PlaylistItemDto> items;
}