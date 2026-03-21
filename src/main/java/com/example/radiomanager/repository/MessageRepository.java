package com.example.radiomanager.repository;

import com.example.radiomanager.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByIsReadOrderBySentAtDesc(boolean isRead);
    List<Message> findAllByOrderBySentAtDesc();
}