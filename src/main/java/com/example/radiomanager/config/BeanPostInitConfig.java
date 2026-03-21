package com.example.radiomanager.config;

import com.example.radiomanager.service.AudioFileService;
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
    private final AudioFileService audioFileService;

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent() {
        log.info("=== Setting dependencies after context refresh ===");
        log.info("BroadcastService: {}", broadcastService);
        log.info("AudioStreamingService: {}", audioStreamingService);
        log.info("AudioFileService: {}", audioFileService);

        broadcastService.setAudioStreamingService(audioStreamingService);
        audioStreamingService.setAudioFileService(audioFileService);

        log.info("=== Dependencies configured successfully ===");
        log.info("AudioStreamingService.getAudioFileService: {}", audioStreamingService.getAudioFileService());
    }
}