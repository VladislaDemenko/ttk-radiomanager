package com.example.radiomanager.service;

import com.example.radiomanager.model.Message;
import com.example.radiomanager.model.User;
import com.example.radiomanager.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;

    public Message sendMessage(User user, String content) {
        Message message = new Message(user, content, LocalDateTime.now());
        return messageRepository.save(message);
    }
}