package com.example.radiomanager.service;

import com.example.radiomanager.dto.MessageWithReplyDto;
import com.example.radiomanager.model.Message;
import com.example.radiomanager.model.User;
import com.example.radiomanager.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserService userService;

    public Message sendMessage(User user, String content) {
        Message message = new Message(user, content, LocalDateTime.now());
        return messageRepository.save(message);
    }

    public List<MessageWithReplyDto> getUnrepliedMessages() {
        List<Message> messages = messageRepository.findUnrepliedMessages();
        return messages.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<MessageWithReplyDto> getRepliedMessages() {
        List<Message> messages = messageRepository.findRepliedMessages();
        return messages.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<MessageWithReplyDto> getAllMessages() {
        List<Message> messages = messageRepository.findAllByOrderBySentAtDesc();
        return messages.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(Long messageId) {
        messageRepository.markAsRead(messageId);
        log.info("Message marked as read: {}", messageId);
    }

    @Transactional
    public void addReply(Long messageId, String replyText, Long broadcasterId) {
        User broadcaster = userService.getActiveUserById(broadcasterId);
        messageRepository.addReply(messageId, replyText, LocalDateTime.now(), broadcaster);
        log.info("Reply added to message: {} by broadcaster: {}", messageId, broadcasterId);
    }

    private MessageWithReplyDto mapToDto(Message message) {
        return new MessageWithReplyDto(
                message.getId(),
                message.getUser().getId(),
                message.getUser().getLogin(),
                message.getUser().getFullName(),
                message.getContent(),
                message.getSentAt(),
                message.isRead(),
                message.getReply(),
                message.getRepliedAt(),
                message.getRepliedBy() != null ? message.getRepliedBy().getFullName() : null
        );
    }
}
