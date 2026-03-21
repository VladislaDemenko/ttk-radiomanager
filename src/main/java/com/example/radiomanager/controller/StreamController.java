package com.example.radiomanager.controller;

import com.example.radiomanager.service.AudioStreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class StreamController {

    private final AudioStreamingService audioStreamingService;

    @MessageMapping("/stream/listener")
    public void handleListener(@Payload Map<String, String> message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String action = message.get("action");

        log.info("Stream listener action: {}, sessionId: {}", action, sessionId);

        if ("add".equals(action)) {
            audioStreamingService.addListener();
        } else if ("remove".equals(action)) {
            audioStreamingService.removeListener();
        }
    }
}