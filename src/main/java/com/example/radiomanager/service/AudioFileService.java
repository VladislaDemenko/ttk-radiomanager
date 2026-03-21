package com.example.radiomanager.service;

import com.example.radiomanager.dto.AudioFileDto;
import com.example.radiomanager.model.AudioFile;
import com.example.radiomanager.model.User;
import com.example.radiomanager.repository.AudioFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioFileService {

    private final AudioFileRepository audioFileRepository;
    private final String uploadDir = "uploads/audio/";

    public AudioFile saveAudioFile(MultipartFile file, User user, String customName, boolean isVoiceNote) throws IOException {
        log.info("Saving audio file: {}, user: {}", file.getOriginalFilename(), user.getLogin());

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalName = file.getOriginalFilename();
        String extension = originalName.substring(originalName.lastIndexOf("."));
        String uniqueFileName = UUID.randomUUID().toString() + extension;
        String filePath = uploadDir + uniqueFileName;

        Files.write(Paths.get(filePath), file.getBytes());

        AudioFile audioFile = new AudioFile();
        audioFile.setUser(user);
        audioFile.setFileName(customName != null && !customName.isEmpty() ? customName : originalName);
        audioFile.setOriginalName(originalName);
        audioFile.setFilePath(filePath);
        audioFile.setFileType(file.getContentType());
        audioFile.setFileSize(file.getSize());
        audioFile.setUploadedAt(LocalDateTime.now());
        audioFile.setVoiceNote(isVoiceNote);

        return audioFileRepository.save(audioFile);
    }

    public AudioFile saveAudioFileFromPath(String filePath, User user, String customName) throws IOException {
        log.info("Saving audio file from path: {}, user: {}", filePath, user.getLogin());

        Path sourcePath = Paths.get(filePath);
        if (!Files.exists(sourcePath)) {
            throw new IOException("File not found: " + filePath);
        }

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalName = sourcePath.getFileName().toString();
        String extension = "";
        int dotIndex = originalName.lastIndexOf(".");
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex);
        }

        String uniqueFileName = UUID.randomUUID().toString() + extension;
        String targetPath = uploadDir + uniqueFileName;

        Files.copy(sourcePath, Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

        long fileSize = Files.size(sourcePath);

        String mimeType = Files.probeContentType(sourcePath);
        if (mimeType == null) {
            if (extension.equalsIgnoreCase(".mp3")) mimeType = "audio/mpeg";
            else if (extension.equalsIgnoreCase(".wav")) mimeType = "audio/wav";
            else if (extension.equalsIgnoreCase(".ogg")) mimeType = "audio/ogg";
            else mimeType = "audio/mpeg";
        }

        AudioFile audioFile = new AudioFile();
        audioFile.setUser(user);
        audioFile.setFileName(customName != null && !customName.isEmpty() ? customName : originalName);
        audioFile.setOriginalName(originalName);
        audioFile.setFilePath(targetPath);
        audioFile.setFileType(mimeType);
        audioFile.setFileSize(fileSize);
        audioFile.setUploadedAt(LocalDateTime.now());
        audioFile.setVoiceNote(false);

        return audioFileRepository.save(audioFile);
    }

    public List<AudioFileDto> getUserAudioFiles(User user) {
        return audioFileRepository.findByUserOrderByUploadedAtDesc(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAudioFile(Long fileId, Long userId) {
        audioFileRepository.deleteByIdAndUserId(fileId, userId);
    }

    public byte[] getAudioData(Long fileId) throws IOException {
        AudioFile audioFile = audioFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Файл не найден"));
        return Files.readAllBytes(Paths.get(audioFile.getFilePath()));
    }

    public byte[] getAudioData(Long fileId, User user) throws IOException {
        AudioFile audioFile = audioFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Файл не найден"));
        if (!audioFile.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Нет доступа к файлу");
        }
        return Files.readAllBytes(Paths.get(audioFile.getFilePath()));
    }

    public String getTrackName(Long fileId) {
        return audioFileRepository.findById(fileId)
                .map(AudioFile::getFileName)
                .orElse("Неизвестный трек");
    }

    public AudioFileDto mapToDto(AudioFile file) {
        return new AudioFileDto(
                file.getId(),
                file.getFileName(),
                file.getOriginalName(),
                file.getFileSize(),
                file.isVoiceNote(),
                file.getUploadedAt().toString()
        );
    }
}