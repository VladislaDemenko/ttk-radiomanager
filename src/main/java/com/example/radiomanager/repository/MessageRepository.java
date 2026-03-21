package com.example.radiomanager.repository;

import com.example.radiomanager.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import com.example.radiomanager.model.User;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByIsReadOrderBySentAtDesc(boolean isRead);

    List<Message> findAllByOrderBySentAtDesc();

    @Query("SELECT m FROM Message m WHERE m.reply IS NULL ORDER BY m.sentAt DESC")
    List<Message> findUnrepliedMessages();

    @Query("SELECT m FROM Message m WHERE m.reply IS NOT NULL ORDER BY m.repliedAt DESC")
    List<Message> findRepliedMessages();

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.isRead = true WHERE m.id = :messageId")
    void markAsRead(@Param("messageId") Long messageId);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.reply = :reply, m.repliedAt = :repliedAt, m.repliedBy = :repliedBy WHERE m.id = :messageId")
    void addReply(@Param("messageId") Long messageId,
                  @Param("reply") String reply,
                  @Param("repliedAt") LocalDateTime repliedAt,
                  @Param("repliedBy") User repliedBy);
}
