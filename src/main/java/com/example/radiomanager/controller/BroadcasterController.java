package com.example.radiomanager.controller;

import com.example.radiomanager.dto.*;
import com.example.radiomanager.service.AudioStreamingService;
import com.example.radiomanager.service.BroadcastService;
import com.example.radiomanager.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final AudioStreamingService audioStreamingService;  // ← ДОБАВЛЯЕМ ЭТУ СТРОКУ

    // Существующие методы...
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
}