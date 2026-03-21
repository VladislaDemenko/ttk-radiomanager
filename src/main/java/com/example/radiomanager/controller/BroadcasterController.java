package com.example.radiomanager.controller;

import com.example.radiomanager.dto.*;
import com.example.radiomanager.model.AudioFile;
import com.example.radiomanager.model.User;
import com.example.radiomanager.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/broadcaster")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class BroadcasterController {

    private final MessageService messageService;
    private final BroadcastService broadcastService;
    private final AudioStreamingService audioStreamingService;
    private final AudioFileService audioFileService;
    private final PlaylistService playlistService;
    private final UserService userService;

    @GetMapping("/messages/unreplied")
    public ResponseEntity<List<MessageWithReplyDto>> getUnrepliedMessages() {
        log.info("Getting unreplied messages");
        List<MessageWithReplyDto> messages = messageService.getUnrepliedMessages();
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/messages/all")
    public ResponseEntity<List<MessageWithReplyDto>> getAllMessages() {
        log.info("Getting all messages");
        List<MessageWithReplyDto> messages = messageService.getAllMessages();
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/messages/replied")
    public ResponseEntity<List<MessageWithReplyDto>> getRepliedMessages() {
        log.info("Getting replied messages");
        List<MessageWithReplyDto> messages = messageService.getRepliedMessages();
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/messages/{id}/read")
    public ResponseEntity<Map<String, String>> markAsRead(@PathVariable Long id) {
        log.info("Marking message as read: {}", id);
        messageService.markAsRead(id);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Сообщение отмечено как прочитанное");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/messages/reply")
    public ResponseEntity<Map<String, String>> replyToMessage(
            @RequestHeader("X-User-Id") Long broadcasterId,
            @RequestBody ReplyToMessageDto replyDto) {

        log.info("Replying to message: {} from broadcaster: {}", replyDto.getMessageId(), broadcasterId);

        messageService.addReply(replyDto.getMessageId(), replyDto.getReply(), broadcasterId);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Ответ отправлен");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    public ResponseEntity<BroadcastInfoDto> getBroadcastInfo() {
        log.info("Getting broadcast info");
        return ResponseEntity.ok(broadcastService.getBroadcastInfo());
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startBroadcast() {
        log.info("Starting broadcast");
        broadcastService.startBroadcast();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Эфир запущен");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> stopBroadcast() {
        log.info("Stopping broadcast");
        broadcastService.stopBroadcast();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Эфир остановлен");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/track")
    public ResponseEntity<Map<String, String>> updateTrack(
            @RequestBody Map<String, String> trackInfo) {

        String track = trackInfo.get("track");
        String artist = trackInfo.get("artist");

        log.info("Updating current track: {} - {}", artist, track);
        broadcastService.updateCurrentTrack(track, artist);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Информация о треке обновлена");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-audio")
    public ResponseEntity<Map<String, String>> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam("trackName") String trackName) {

        log.info("=== UPLOAD AUDIO CALLED ===");
        log.info("File name: {}", file.getOriginalFilename());
        log.info("File size: {} bytes", file.getSize());
        log.info("File type: {}", file.getContentType());
        log.info("Track name: {}", trackName);

        Map<String, String> response = new HashMap<>();

        try {
            if (file.getSize() > 50 * 1024 * 1024) {
                log.warn("File too large: {} bytes", file.getSize());
                response.put("status", "error");
                response.put("message", "Файл не должен превышать 50 МБ");
                return ResponseEntity.badRequest().body(response);
            }

            String fileName = file.getOriginalFilename();
            if (fileName != null) {
                int lastDot = fileName.lastIndexOf(".");
                if (lastDot > 0) {
                    String extension = fileName.substring(lastDot + 1).toLowerCase();
                    if (!extension.equals("mp3") && !extension.equals("wav") && !extension.equals("ogg")) {
                        log.warn("Invalid file format: {}", extension);
                        response.put("status", "error");
                        response.put("message", "Поддерживаются только аудио файлы (MP3, WAV, OGG)");
                        return ResponseEntity.badRequest().body(response);
                    }
                } else {
                    response.put("status", "error");
                    response.put("message", "Файл должен иметь расширение .mp3, .wav или .ogg");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            byte[] audioData = file.getBytes();
            log.info("Audio data size: {} bytes", audioData.length);

            if (audioData.length == 0) {
                response.put("status", "error");
                response.put("message", "Файл пуст");
                return ResponseEntity.badRequest().body(response);
            }

            audioStreamingService.startStreaming(audioData, trackName);
            log.info("Streaming started for track: {}", trackName);

            broadcastService.updateCurrentTrack(trackName, "Трансляция");

            if (!broadcastService.isLive()) {
                broadcastService.startBroadcast();
                log.info("Broadcast auto-started");
            }

            response.put("status", "success");
            response.put("message", "Трек загружен и начал воспроизводиться");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("IO Error uploading audio: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "Ошибка при чтении файла: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Unexpected error uploading audio: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/stop-stream")
    public ResponseEntity<Map<String, String>> stopStream() {
        log.info("Stopping audio stream");
        audioStreamingService.stopStreaming();

        broadcastService.updateCurrentTrack("Стрим остановлен", "");

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Трансляция остановлена");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stream-status")
    public ResponseEntity<Map<String, Object>> getStreamStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("isStreaming", audioStreamingService.isStreaming());
        response.put("currentTrack", audioStreamingService.getCurrentTrackName());
        response.put("listeners", audioStreamingService.getActiveListenersCount());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/media/upload")
    public ResponseEntity<?> uploadToMediaLibrary(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Uploading to media library: {}, userId: {}", file.getOriginalFilename(), userId);

        try {
            User user = userService.findById(userId);

            if (file.getSize() > 50 * 1024 * 1024) {
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Файл не должен превышать 50 МБ");
                return ResponseEntity.badRequest().body(response);
            }

            AudioFile audioFile = audioFileService.saveAudioFile(file, user, null, false);

            AudioFileDto audioFileDto = audioFileService.mapToDto(audioFile);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("file", audioFileDto);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("IO Error uploading file: {}", e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Ошибка при сохранении файла: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/media")
    public ResponseEntity<List<AudioFileDto>> getMediaLibrary(@RequestHeader("X-User-Id") Long userId) {
        log.info("Getting media library for user: {}", userId);
        User user = userService.findById(userId);
        return ResponseEntity.ok(audioFileService.getUserAudioFiles(user));
    }

    @DeleteMapping("/media/{fileId}")
    public ResponseEntity<?> deleteAudioFile(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Deleting audio file: {}, userId: {}", fileId, userId);
        audioFileService.deleteAudioFile(fileId, userId);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Файл удален");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playlists")
    public ResponseEntity<PlaylistDto> createPlaylist(
            @RequestBody Map<String, String> request,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Creating playlist for user: {}", userId);
        User user = userService.findById(userId);
        String name = request.get("name");

        if (name == null || name.trim().isEmpty()) {
            name = "Новый плейлист";
        }

        return ResponseEntity.ok(playlistService.createPlaylist(user, name));
    }

    @GetMapping("/playlists")
    public ResponseEntity<List<PlaylistDto>> getUserPlaylists(@RequestHeader("X-User-Id") Long userId) {
        log.info("Getting playlists for user: {}", userId);
        User user = userService.findById(userId);
        return ResponseEntity.ok(playlistService.getUserPlaylists(user));
    }

    @GetMapping("/playlists/active")
    public ResponseEntity<PlaylistDto> getActivePlaylist(@RequestHeader("X-User-Id") Long userId) {
        log.info("Getting active playlist for user: {}", userId);
        User user = userService.findById(userId);
        PlaylistDto active = playlistService.getActivePlaylist(user);
        return ResponseEntity.ok(active);
    }

    @PostMapping("/playlists/{playlistId}/items")
    public ResponseEntity<PlaylistDto> addToPlaylist(
            @PathVariable Long playlistId,
            @RequestBody Map<String, Long> request,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Adding to playlist: {}, fileId: {}, userId: {}", playlistId, request.get("audioFileId"), userId);
        User user = userService.findById(userId);
        Long audioFileId = request.get("audioFileId");

        return ResponseEntity.ok(playlistService.addToPlaylist(playlistId, audioFileId, user));
    }

    @DeleteMapping("/playlists/{playlistId}/items/{itemId}")
    public ResponseEntity<?> removeFromPlaylist(
            @PathVariable Long playlistId,
            @PathVariable Long itemId,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Removing from playlist: {}, itemId: {}, userId: {}", playlistId, itemId, userId);
        User user = userService.findById(userId);
        playlistService.removeFromPlaylist(playlistId, itemId, user);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playlists/{playlistId}/reorder")
    public ResponseEntity<?> reorderPlaylist(
            @PathVariable Long playlistId,
            @RequestBody Map<String, List<Long>> request,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Reordering playlist: {}, userId: {}", playlistId, userId);
        User user = userService.findById(userId);
        List<Long> itemIds = request.get("itemIds");

        playlistService.reorderPlaylist(playlistId, itemIds, user);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playlists/{playlistId}/activate")
    public ResponseEntity<?> activatePlaylist(
            @PathVariable Long playlistId,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Activating playlist: {}, userId: {}", playlistId, userId);
        User user = userService.findById(userId);
        playlistService.setActivePlaylist(playlistId, user);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Плейлист активирован и начал воспроизведение");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playlists/{playlistId}/toggle-loop")
    public ResponseEntity<?> toggleLooping(
            @PathVariable Long playlistId,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Toggling loop for playlist: {}, userId: {}", playlistId, userId);
        User user = userService.findById(userId);
        playlistService.toggleLooping(playlistId, user);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playlists/{playlistId}/toggle-shuffle")
    public ResponseEntity<?> toggleShuffling(
            @PathVariable Long playlistId,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Toggling shuffle for playlist: {}, userId: {}", playlistId, userId);
        User user = userService.findById(userId);
        playlistService.toggleShuffling(playlistId, user);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playback/next")
    public ResponseEntity<?> nextTrack() {
        log.info("Next track requested");
        audioStreamingService.nextTrack();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playback/prev")
    public ResponseEntity<?> previousTrack() {
        log.info("Previous track requested");
        audioStreamingService.previousTrack();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/playback/status")
    public ResponseEntity<PlaylistStatusDto> getPlaybackStatus() {
        log.info("Getting playback status");
        return ResponseEntity.ok(audioStreamingService.getPlaylistStatus());
    }

    @PostMapping("/playback/play")
    public ResponseEntity<?> play() {
        log.info("Play requested");

        Map<String, String> response = new HashMap<>();

        if (!audioStreamingService.isStreaming()) {
            PlaylistDto activePlaylist = playlistService.getActivePlaylist(
                    userService.findById(1L) // Временно, нужно получать из сессии
            );

            if (activePlaylist != null && activePlaylist.getItems() != null && !activePlaylist.getItems().isEmpty()) {
                List<Long> audioFileIds = activePlaylist.getItems().stream()
                        .map(PlaylistItemDto::getAudioFileId)
                        .collect(java.util.stream.Collectors.toList());

                audioStreamingService.startPlaylist(audioFileIds,
                        activePlaylist.isLooping(), activePlaylist.isShuffling());
                response.put("message", "Воспроизведение плейлиста начато");
            } else {
                response.put("message", "Нет активного плейлиста для воспроизведения");
            }
        } else {
            response.put("message", "Воспроизведение уже идет");
        }

        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playback/pause")
    public ResponseEntity<?> pause() {
        log.info("Pause requested");
        audioStreamingService.stopStreaming();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Воспроизведение приостановлено");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playlists/{playlistId}/add-from-fs")
    public ResponseEntity<?> addTrackFromFileSystem(
            @PathVariable Long playlistId,
            @RequestBody Map<String, String> request,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Adding track from file system to playlist: {}", playlistId);

        try {
            String filePath = request.get("filePath");
            String trackName = request.get("trackName");

            if (filePath == null || filePath.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Путь к файлу не указан");
                return ResponseEntity.badRequest().body(error);
            }

            // Проверяем существование файла
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Файл не найден по пути: " + filePath);
                return ResponseEntity.badRequest().body(error);
            }

            // Проверяем формат файла
            String fileName = path.getFileName().toString().toLowerCase();
            if (!fileName.endsWith(".mp3") && !fileName.endsWith(".wav") && !fileName.endsWith(".ogg")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Поддерживаются только MP3, WAV и OGG файлы");
                return ResponseEntity.badRequest().body(error);
            }

            // Проверяем размер файла (максимум 50 МБ)
            long fileSize = Files.size(path);
            if (fileSize > 50 * 1024 * 1024) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Файл не должен превышать 50 МБ");
                return ResponseEntity.badRequest().body(error);
            }

            User user = userService.findById(userId);

            // Сохраняем файл в медиатеку
            AudioFile audioFile = audioFileService.saveAudioFileFromPath(
                    filePath,
                    user,
                    trackName != null && !trackName.isEmpty() ? trackName : path.getFileName().toString()
            );

            // Добавляем в плейлист
            PlaylistDto updatedPlaylist = playlistService.addToPlaylist(playlistId, audioFile.getId(), user);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Трек добавлен в плейлист");
            response.put("playlist", updatedPlaylist);
            response.put("audioFile", audioFileService.mapToDto(audioFile));

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error adding track from file system: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Ошибка при чтении файла: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        } catch (Exception e) {
            log.error("Error adding track from file system: ", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // НОВЫЙ МЕТОД: Получить список доступных аудиофайлов из директории
    @GetMapping("/fs-audio-files")
    public ResponseEntity<?> getAudioFilesFromFileSystem(
            @RequestParam(defaultValue = "./uploads/audio") String directory) {

        log.info("Getting audio files from directory: {}", directory);

        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Директория не найдена: " + directory);
                return ResponseEntity.badRequest().body(error);
            }

            List<Map<String, String>> audioFiles = Files.list(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg");
                    })
                    .map(path -> {
                        Map<String, String> fileInfo = new HashMap<>();
                        fileInfo.put("name", path.getFileName().toString());
                        fileInfo.put("path", path.toString());
                        try {
                            long size = Files.size(path);
                            fileInfo.put("size", formatFileSize(size));
                        } catch (IOException e) {
                            fileInfo.put("size", "Unknown");
                        }
                        return fileInfo;
                    })
                    .collect(java.util.stream.Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("directory", directory);
            response.put("files", audioFiles);
            response.put("count", audioFiles.size());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error listing audio files: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Ошибка при чтении директории: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }
}