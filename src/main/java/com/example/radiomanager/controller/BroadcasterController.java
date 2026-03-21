package com.example.radiomanager.controller;

import com.example.radiomanager.dto.BroadcastInfoDto;
import com.example.radiomanager.dto.MessageWithReplyDto;
import com.example.radiomanager.dto.ReplyToMessageDto;
import com.example.radiomanager.service.BroadcastService;
import com.example.radiomanager.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}