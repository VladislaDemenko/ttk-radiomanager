package com.example.radiomanager.config;

import com.example.radiomanager.service.AudioStreamingService;
import com.example.radiomanager.service.BroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BeanPostInitConfig {

    private final BroadcastService broadcastService;
    private final AudioStreamingService audioStreamingService;

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent() {
        log.info("Setting AudioStreamingService reference in BroadcastService");
        broadcastService.setAudioStreamingService(audioStreamingService);
    }
}
