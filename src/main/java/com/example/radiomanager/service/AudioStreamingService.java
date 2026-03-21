package com.example.radiomanager.service;

import com.example.radiomanager.dto.AudioChunkDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class AudioStreamingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicInteger listenersCount = new AtomicInteger(0);

    private boolean isStreaming = false;
    private String currentTrackName = "";
    private Thread streamingThread;
    private byte[] currentAudioData;

    // Константы для стриминга
    private static final int CHUNK_SIZE = 8192; // 8KB chunks
    private static final int CHUNK_DELAY_MS = 100; // 100ms между чанками

    public AudioStreamingService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void startStreaming(byte[] audioData, String trackName) {
        if (isStreaming) {
            stopStreaming();
        }

        this.currentAudioData = audioData;
        this.currentTrackName = trackName;
        this.isStreaming = true;

        streamingThread = new Thread(() -> {
            try {
                streamAudioData();
            } catch (Exception e) {
                log.error("Error in streaming thread: {}", e.getMessage());
                isStreaming = false;
            }
        });
        streamingThread.start();

        log.info("Started streaming track: {}", trackName);
    }

    private void streamAudioData() {
        if (currentAudioData == null) return;

        int offset = 0;
        int chunkNumber = 0;

        while (offset < currentAudioData.length && isStreaming) {
            int end = Math.min(offset + CHUNK_SIZE, currentAudioData.length);
            byte[] chunk = new byte[end - offset];
            System.arraycopy(currentAudioData, offset, chunk, 0, chunk.length);

            AudioChunkDto audioChunk = new AudioChunkDto(
                    chunkNumber++,
                    Base64.getEncoder().encodeToString(chunk),
                    end >= currentAudioData.length,
                    currentTrackName
            );

            messagingTemplate.convertAndSend("/topic/stream", audioChunk);

            offset = end;

            try {
                Thread.sleep(CHUNK_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (isStreaming && offset >= currentAudioData.length) {
            AudioChunkDto endSignal = new AudioChunkDto(
                    chunkNumber,
                    "",
                    true,
                    currentTrackName
            );
            messagingTemplate.convertAndSend("/topic/stream", endSignal);
        }

        isStreaming = false;
        log.info("Finished streaming track: {}", currentTrackName);
    }

    public void stopStreaming() {
        if (streamingThread != null && streamingThread.isAlive()) {
            isStreaming = false;
            streamingThread.interrupt();
            try {
                streamingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        isStreaming = false;
        currentAudioData = null;
        currentTrackName = "";
        log.info("Stopped streaming");
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public String getCurrentTrackName() {
        return currentTrackName;
    }

    public void addListener() {
        int count = listenersCount.incrementAndGet();
        log.info("Listener added. Total: {}", count);
    }

    public void removeListener() {
        int count = listenersCount.decrementAndGet();
        log.info("Listener removed. Total: {}", count);
    }

    public int getActiveListenersCount() {
        return listenersCount.get();
    }
}
