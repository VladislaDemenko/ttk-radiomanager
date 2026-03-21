package com.example.radiomanager.service;

import com.example.radiomanager.dto.BroadcastInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class BroadcastService {

    private boolean isLive = false;
    private String currentTrack = "Ожидание начала эфира";
    private String currentArtist = "";
    private LocalDateTime startedAt = null;
    private final AtomicInteger listenersCount = new AtomicInteger(0);

    // Ссылка на AudioStreamingService, устанавливается через setter
    private AudioStreamingService audioStreamingService;

    public BroadcastInfoDto getBroadcastInfo() {
        int listenerCount = listenersCount.get();

        // Если есть audioStreamingService, используем его количество слушателей
        if (audioStreamingService != null) {
            listenerCount = audioStreamingService.getActiveListenersCount();
        }

        return new BroadcastInfoDto(
                isLive,
                currentTrack,
                currentArtist,
                listenerCount,
                startedAt
        );
    }

    public void startBroadcast() {
        if (!isLive) {
            isLive = true;
            startedAt = LocalDateTime.now();
            log.info("Broadcast started at: {}", startedAt);
        }
    }

    public void stopBroadcast() {
        if (isLive) {
            isLive = false;
            currentTrack = "Эфир завершен";
            currentArtist = "";
            log.info("Broadcast stopped");
        }
    }

    public void updateCurrentTrack(String track, String artist) {
        this.currentTrack = track;
        this.currentArtist = artist;
        log.info("Current track updated: {} - {}", artist, track);
    }

    public void addListener() {
        listenersCount.incrementAndGet();
        log.info("Listener added. Total: {}", listenersCount.get());

        // Синхронизируем с audioStreamingService
        if (audioStreamingService != null) {
            audioStreamingService.addListener();
        }
    }

    public void removeListener() {
        listenersCount.decrementAndGet();
        log.info("Listener removed. Total: {}", listenersCount.get());

        // Синхронизируем с audioStreamingService
        if (audioStreamingService != null) {
            audioStreamingService.removeListener();
        }
    }

    // Setter для внедрения AudioStreamingService (избегаем циклической зависимости)
    public void setAudioStreamingService(AudioStreamingService audioStreamingService) {
        this.audioStreamingService = audioStreamingService;
    }

    public boolean isLive() {
        return isLive;
    }

    public String getCurrentTrack() {
        return currentTrack;
    }

    public String getCurrentArtist() {
        return currentArtist;
    }
}
