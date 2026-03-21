package com.example.radiomanager.repository;

import com.example.radiomanager.model.AudioFile;
import com.example.radiomanager.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AudioFileRepository extends JpaRepository<AudioFile, Long> {

    List<AudioFile> findByUserAndIsVoiceNoteOrderByUploadedAtDesc(User user, boolean isVoiceNote);

    List<AudioFile> findByUserOrderByUploadedAtDesc(User user);

    @Modifying
    @Transactional
    @Query("DELETE FROM AudioFile a WHERE a.id = :id AND a.user.id = :userId")
    void deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}