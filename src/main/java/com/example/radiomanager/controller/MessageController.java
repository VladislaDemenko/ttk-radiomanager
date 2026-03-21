package com.example.radiomanager.controller;

import com.example.radiomanager.dto.MessageRequestDto;
import com.example.radiomanager.dto.MessageResponseDto;
import com.example.radiomanager.model.Message;
import com.example.radiomanager.model.User;
import com.example.radiomanager.service.MessageService;
import com.example.radiomanager.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;
    private final UserService userService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendMessage(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody MessageRequestDto request) {

        log.info("Sending message from user ID: {}", userId);

        User user = userService.findById(userId);
        Message message = messageService.sendMessage(user, request.getContent());

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Сообщение отправлено");
        return ResponseEntity.ok(response);
    }
}