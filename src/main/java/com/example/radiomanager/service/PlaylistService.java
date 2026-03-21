package com.example.radiomanager.service;

import com.example.radiomanager.dto.PlaylistDto;
import com.example.radiomanager.dto.PlaylistItemDto;
import com.example.radiomanager.model.AudioFile;
import com.example.radiomanager.model.Playlist;
import com.example.radiomanager.model.PlaylistItem;
import com.example.radiomanager.model.User;
import com.example.radiomanager.repository.AudioFileRepository;
import com.example.radiomanager.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final AudioFileRepository audioFileRepository;
    private final AudioStreamingService audioStreamingService;

    @Transactional
    public PlaylistDto createPlaylist(User user, String name) {
        log.info("=== PLAYLIST SERVICE CREATE ===");
        log.info("User: {} (ID: {})", user.getLogin(), user.getId());
        log.info("Name: {}", name);

        try {
            Playlist playlist = new Playlist();
            playlist.setUser(user);
            playlist.setName(name.trim());
            playlist.setCreatedAt(LocalDateTime.now());
            playlist.setActive(false);
            playlist.setLooping(false);
            playlist.setShuffling(false);

            playlist.setItems(new ArrayList<>());

            log.info("Saving playlist to database...");
            Playlist savedPlaylist = playlistRepository.save(playlist);
            log.info("Playlist saved successfully with ID: {}", savedPlaylist.getId());

            PlaylistDto dto = new PlaylistDto();
            dto.setId(savedPlaylist.getId());
            dto.setName(savedPlaylist.getName());
            dto.setActive(savedPlaylist.isActive());
            dto.setLooping(savedPlaylist.isLooping());
            dto.setShuffling(savedPlaylist.isShuffling());
            dto.setItems(new ArrayList<>()); // Пустой список, так как треков еще нет

            log.info("Returning DTO: id={}, name={}", dto.getId(), dto.getName());
            return dto;

        } catch (Exception e) {
            log.error("Error in playlist creation: ", e);
            throw new RuntimeException("Failed to create playlist: " + e.getMessage(), e);
        }
    }

    public List<PlaylistDto> getUserPlaylists(User user) {
        log.info("Getting playlists for user: {}", user.getLogin());
        List<Playlist> playlists = playlistRepository.findByUserOrderByCreatedAtDesc(user);
        return playlists.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public PlaylistDto getActivePlaylist(User user) {
        Optional<Playlist> activePlaylist = playlistRepository.findByUserAndIsActiveTrue(user);
        return activePlaylist.map(this::mapToDto).orElse(null);
    }

    @Transactional
    public PlaylistDto addToPlaylist(Long playlistId, Long audioFileId, User user) {
        log.info("Adding file {} to playlist {}", audioFileId, playlistId);

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Плейлист не найден"));

        if (!playlist.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Нет доступа к плейлисту");
        }

        AudioFile audioFile = audioFileRepository.findById(audioFileId)
                .orElseThrow(() -> new RuntimeException("Аудиофайл не найден"));

        if (playlist.getItems() == null) {
            playlist.setItems(new ArrayList<>());
        }

        int position = playlist.getItems().size();

        PlaylistItem item = new PlaylistItem();
        item.setPlaylist(playlist);
        item.setAudioFile(audioFile);
        item.setPosition(position);

        playlist.getItems().add(item);

        Playlist savedPlaylist = playlistRepository.save(playlist);
        log.info("File added successfully, now playlist has {} items", savedPlaylist.getItems().size());

        return mapToDto(savedPlaylist);
    }

    @Transactional
    public void removeFromPlaylist(Long playlistId, Long itemId, User user) {
        log.info("Removing item {} from playlist {}", itemId, playlistId);

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Плейлист не найден"));

        if (!playlist.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Нет доступа к плейлисту");
        }

        if (playlist.getItems() == null) {
            return;
        }

        playlist.getItems().removeIf(item -> item.getId().equals(itemId));

        for (int i = 0; i < playlist.getItems().size(); i++) {
            playlist.getItems().get(i).setPosition(i);
        }

        playlistRepository.save(playlist);
        log.info("Item removed successfully");
    }

    @Transactional
    public void reorderPlaylist(Long playlistId, List<Long> itemIds, User user) {
        log.info("Reordering playlist {}", playlistId);

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Плейлист не найден"));

        if (!playlist.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Нет доступа к плейлисту");
        }

        if (playlist.getItems() == null || playlist.getItems().isEmpty()) {
            return;
        }

        Map<Long, PlaylistItem> itemMap = playlist.getItems().stream()
                .collect(Collectors.toMap(PlaylistItem::getId, item -> item));

        playlist.getItems().clear();

        for (int i = 0; i < itemIds.size(); i++) {
            PlaylistItem item = itemMap.get(itemIds.get(i));
            if (item != null) {
                item.setPosition(i);
                playlist.getItems().add(item);
            }
        }

        playlistRepository.save(playlist);
        log.info("Playlist reordered successfully");
    }

    @Transactional
    public void setActivePlaylist(Long playlistId, User user) {
        log.info("Setting active playlist: {}", playlistId);

        // Деактивируем все плейлисты пользователя
        playlistRepository.deactivateAllPlaylists(user.getId());

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Плейлист не найден"));

        if (!playlist.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Нет доступа к плейлисту");
        }

        playlist.setActive(true);
        Playlist savedPlaylist = playlistRepository.save(playlist);
        log.info("Playlist activated: {}", savedPlaylist.getId());

        // Запускаем воспроизведение
        startPlayingPlaylist(savedPlaylist, user);
    }

    @Transactional
    public void toggleLooping(Long playlistId, User user) {
        log.info("Toggling loop for playlist: {}", playlistId);

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Плейлист не найден"));

        if (!playlist.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Нет доступа к плейлисту");
        }

        playlist.setLooping(!playlist.isLooping());
        playlistRepository.save(playlist);

        if (playlist.isActive()) {
            audioStreamingService.setLooping(playlist.isLooping());
        }

        log.info("Looping set to: {}", playlist.isLooping());
    }

    @Transactional
    public void toggleShuffling(Long playlistId, User user) {
        log.info("Toggling shuffle for playlist: {}", playlistId);

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Плейлист не найден"));

        if (!playlist.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Нет доступа к плейлисту");
        }

        playlist.setShuffling(!playlist.isShuffling());
        playlistRepository.save(playlist);

        if (playlist.isActive()) {
            audioStreamingService.setShuffling(playlist.isShuffling());
            if (playlist.isShuffling()) {
                audioStreamingService.shuffleQueue();
            }
        }

        log.info("Shuffling set to: {}", playlist.isShuffling());
    }

    private void startPlayingPlaylist(Playlist playlist, User user) {
        log.info("Starting playback for playlist: {}, items count: {}",
                playlist.getId(),
                playlist.getItems() != null ? playlist.getItems().size() : 0);

        if (playlist.getItems() == null || playlist.getItems().isEmpty()) {
            log.warn("Cannot start empty playlist");
            return;
        }

        List<Long> audioFileIds = playlist.getItems().stream()
                .sorted(Comparator.comparingInt(PlaylistItem::getPosition))
                .map(item -> item.getAudioFile().getId())
                .collect(Collectors.toList());

        log.info("Audio file IDs to play: {}", audioFileIds);

        audioStreamingService.startPlaylist(audioFileIds, playlist.isLooping(), playlist.isShuffling());
    }

    private PlaylistDto mapToDto(Playlist playlist) {
        List<PlaylistItemDto> items = new ArrayList<>();

        if (playlist.getItems() != null && !playlist.getItems().isEmpty()) {
            items = playlist.getItems().stream()
                    .sorted(Comparator.comparingInt(PlaylistItem::getPosition))
                    .map(item -> {
                        AudioFile audioFile = item.getAudioFile();
                        return new PlaylistItemDto(
                                item.getId(),
                                audioFile.getId(),
                                audioFile.getFileName(),
                                audioFile.getOriginalName(),
                                item.getPosition()
                        );
                    })
                    .collect(Collectors.toList());
        }

        return new PlaylistDto(
                playlist.getId(),
                playlist.getName(),
                playlist.isActive(),
                playlist.isLooping(),
                playlist.isShuffling(),
                items
        );
    }
}