package com.example.radiomanager.repository;

import com.example.radiomanager.model.Playlist;
import com.example.radiomanager.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByUserOrderByCreatedAtDesc(User user);

    Optional<Playlist> findByUserAndIsActiveTrue(User user);

    @Modifying
    @Transactional
    @Query("UPDATE Playlist p SET p.isActive = false WHERE p.user.id = :userId")
    void deactivateAllPlaylists(@Param("userId") Long userId);
}