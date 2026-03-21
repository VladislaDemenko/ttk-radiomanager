package com.example.radiomanager.service;

import com.example.radiomanager.dto.AudioChunkDto;
import com.example.radiomanager.dto.PlaylistStatusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private volatile boolean shouldStop = false;

    private List<Long> currentPlaylist = new ArrayList<>();
    private List<Long> shuffledPlaylist = new ArrayList<>();
    private int currentTrackIndex = 0;
    private boolean isLooping = false;
    private boolean isShuffling = false;
    private final Map<Long, byte[]> audioCache = new ConcurrentHashMap<>();

    private AudioFileService audioFileService;

    private static final int CHUNK_SIZE = 16384;
    private static final int CHUNK_DELAY_MS = 50;

    public AudioStreamingService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void setAudioFileService(AudioFileService audioFileService) {
        this.audioFileService = audioFileService;
        log.info("AudioFileService injected successfully");
    }

    public AudioFileService getAudioFileService() {
        return audioFileService;
    }

    public void startStreaming(byte[] audioData, String trackName) {
        if (isStreaming) {
            stopStreaming();
        }

        this.currentAudioData = audioData;
        this.currentTrackName = trackName;
        this.isStreaming = true;
        this.shouldStop = false;

        streamingThread = new Thread(() -> {
            try {
                streamAudioData();
            } catch (Exception e) {
                log.error("Error in streaming thread: {}", e.getMessage(), e);
                isStreaming = false;
            }
        });
        streamingThread.start();

        log.info("Started streaming track: {}", trackName);
        broadcastTrackInfo(trackName);
        broadcastPlaybackStatus();
    }

    private void streamAudioData() {
        if (currentAudioData == null) {
            log.warn("No audio data to stream");
            return;
        }

        int offset = 0;
        int chunkNumber = 0;
        int totalChunks = (int) Math.ceil((double) currentAudioData.length / CHUNK_SIZE);
        log.info("Starting streaming, total chunks: {}", totalChunks);

        while (offset < currentAudioData.length && !shouldStop) {
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
                log.info("Streaming interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!shouldStop && offset >= currentAudioData.length) {
            log.info("Sending end signal for track: {}", currentTrackName);
            AudioChunkDto endSignal = new AudioChunkDto(
                    chunkNumber,
                    "",
                    true,
                    currentTrackName
            );
            messagingTemplate.convertAndSend("/topic/stream", endSignal);

            if (!shouldStop) {
                onTrackFinished();
            }
        }

        isStreaming = false;
        log.info("Finished streaming track: {}", currentTrackName);
    }

    public void stopStreaming() {
        if (streamingThread != null && streamingThread.isAlive()) {
            shouldStop = true;
            streamingThread.interrupt();
            try {
                streamingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        isStreaming = false;
        currentAudioData = null;
        log.info("Stopped streaming");
        broadcastPlaybackStatus();
    }

    private void onTrackFinished() {
        log.info("Track finished, moving to next track");
        nextTrack();
    }

    public void startPlaylist(List<Long> audioFileIds, boolean looping, boolean shuffling) {
        log.info("Starting playlist with {} tracks, looping: {}, shuffling: {}",
                audioFileIds.size(), looping, shuffling);

        stopStreaming();

        this.currentPlaylist = new ArrayList<>(audioFileIds);
        this.isLooping = looping;
        this.isShuffling = shuffling;

        if (shuffling) {
            shuffleQueue();
        } else {
            shuffledPlaylist = new ArrayList<>(currentPlaylist);
        }

        currentTrackIndex = 0;

        if (!shuffledPlaylist.isEmpty()) {
            playCurrentTrack();
        } else {
            log.warn("Playlist is empty");
        }

        broadcastPlaybackStatus();
    }

    public void shuffleQueue() {
        if (currentPlaylist.isEmpty()) return;

        shuffledPlaylist = new ArrayList<>(currentPlaylist);
        Collections.shuffle(shuffledPlaylist);

        log.info("Playlist shuffled: {} tracks", shuffledPlaylist.size());
        broadcastPlaybackStatus();
    }

    public void nextTrack() {
        if (shuffledPlaylist.isEmpty()) {
            log.info("No tracks in playlist");
            return;
        }

        currentTrackIndex++;
        log.info("Moving to next track, index: {}", currentTrackIndex);

        if (currentTrackIndex >= shuffledPlaylist.size()) {
            if (isLooping) {
                log.info("Looping enabled, restarting playlist");
                currentTrackIndex = 0;
                if (isShuffling) {
                    shuffleQueue();
                }
            } else {
                log.info("End of playlist, stopping");
                stopStreaming();
                broadcastPlaybackStatus();
                return;
            }
        }

        playCurrentTrack();
        broadcastPlaybackStatus();
    }

    public void previousTrack() {
        if (shuffledPlaylist.isEmpty()) return;

        currentTrackIndex--;

        if (currentTrackIndex < 0) {
            if (isLooping) {
                currentTrackIndex = shuffledPlaylist.size() - 1;
            } else {
                currentTrackIndex = 0;
                return;
            }
        }

        playCurrentTrack();
        broadcastPlaybackStatus();
    }

    private void playCurrentTrack() {
        if (currentTrackIndex >= shuffledPlaylist.size()) {
            log.warn("Current track index out of bounds: {}", currentTrackIndex);
            return;
        }

        Long audioFileId = shuffledPlaylist.get(currentTrackIndex);
        log.info("Playing track {} of {}, file ID: {}",
                currentTrackIndex + 1, shuffledPlaylist.size(), audioFileId);

        byte[] audioData = audioCache.get(audioFileId);

        if (audioData != null) {
            startStreaming(audioData, getTrackName(audioFileId));
        } else {
            loadAndPlayTrack(audioFileId);
        }
    }

    private void loadAndPlayTrack(Long audioFileId) {
        if (audioFileService == null) {
            log.error("AudioFileService not set!");
            return;
        }

        log.info("Loading audio file from database: {}", audioFileId);

        CompletableFuture.supplyAsync(() -> {
            try {
                return audioFileService.getAudioData(audioFileId);
            } catch (IOException e) {
                log.error("Error loading audio file {}: {}", audioFileId, e.getMessage());
                return null;
            }
        }).thenAccept(audioData -> {
            if (audioData != null && audioData.length > 0) {
                audioCache.put(audioFileId, audioData);
                startStreaming(audioData, getTrackName(audioFileId));
            } else {
                log.error("Failed to load audio file: {}", audioFileId);
                nextTrack();
            }
        }).exceptionally(ex -> {
            log.error("Error in async loading: {}", ex.getMessage());
            nextTrack();
            return null;
        });
    }

    private String getTrackName(Long audioFileId) {
        if (audioFileService != null) {
            try {
                return audioFileService.getTrackName(audioFileId);
            } catch (Exception e) {
                log.warn("Could not get track name: {}", e.getMessage());
            }
        }
        return "Трек " + (currentTrackIndex + 1);
    }

    public void setLooping(boolean looping) {
        this.isLooping = looping;
        log.info("Looping set to: {}", looping);
        broadcastPlaybackStatus();
    }

    public void setShuffling(boolean shuffling) {
        this.isShuffling = shuffling;
        if (shuffling && !shuffledPlaylist.isEmpty()) {
            shuffleQueue();
            currentTrackIndex = 0;
            playCurrentTrack();
        }
        log.info("Shuffling set to: {}", shuffling);
        broadcastPlaybackStatus();
    }

    public boolean isLooping() {
        return isLooping;
    }

    public boolean isShuffling() {
        return isShuffling;
    }

    public PlaylistStatusDto getPlaylistStatus() {
        String statusText;
        if (shuffledPlaylist.isEmpty()) {
            statusText = "Нет треков в плейлисте";
        } else if (currentTrackIndex >= shuffledPlaylist.size()) {
            statusText = "Воспроизведение завершено";
        } else if (isStreaming) {
            statusText = "Трек " + (currentTrackIndex + 1) + " из " + shuffledPlaylist.size();
        } else {
            statusText = "Остановлено";
        }

        return new PlaylistStatusDto(
                currentTrackIndex,
                shuffledPlaylist.size(),
                isLooping,
                isShuffling,
                statusText
        );
    }

    public List<Long> getCurrentPlaylist() {
        return new ArrayList<>(shuffledPlaylist);
    }

    public int getCurrentTrackIndex() {
        return currentTrackIndex;
    }

    public void addListener() {
        int count = listenersCount.incrementAndGet();
        log.info("Listener added. Total: {}", count);
        broadcastListenerCount();
    }

    public void removeListener() {
        int count = listenersCount.decrementAndGet();
        log.info("Listener removed. Total: {}", count);
        broadcastListenerCount();
    }

    public int getActiveListenersCount() {
        return listenersCount.get();
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public String getCurrentTrackName() {
        return currentTrackName;
    }

    private void broadcastTrackInfo(String trackName) {
        Map<String, String> trackInfo = new HashMap<>();
        trackInfo.put("track", trackName);
        trackInfo.put("artist", "Сейчас в эфире");
        messagingTemplate.convertAndSend("/topic/track-info", trackInfo);
    }

    private void broadcastListenerCount() {
        Map<String, Integer> listenerInfo = new HashMap<>();
        listenerInfo.put("count", listenersCount.get());
        messagingTemplate.convertAndSend("/topic/listeners", listenerInfo);
    }

    private void broadcastPlaybackStatus() {
        PlaylistStatusDto status = getPlaylistStatus();
        messagingTemplate.convertAndSend("/topic/playback-status", status);
    }
}