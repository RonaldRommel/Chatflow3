package com.chatflow.server.handler;

import com.chatflow.server.model.ChatMessage;
import com.chatflow.server.model.UserInfo;
import com.chatflow.server.rabbit.RabbitMQSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.*;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final RabbitMQSender rabbitMQSender;
    private final SessionManager sessionManager;
    public WebSocketHandler(ObjectMapper objectMapper, Validator validator,
                            RabbitMQSender rabbitMQSender, SessionManager sessionManager) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.rabbitMQSender = rabbitMQSender;
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String path = session.getUri().getPath();
        String roomId = path.substring(path.lastIndexOf('/') + 1);
        System.out.println("New WebSocket connection for room: " + roomId);
        sessionManager.addSession(roomId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        try {
            ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);

            Set<ConstraintViolation<ChatMessage>> violations = validator.validate(chatMessage);
            if (!violations.isEmpty()) {
                return;
            }

            Map<String, Object> ackResponse = Map.of(
                    "messageId", chatMessage.getMessageId(),
                    "status", "RECEIVED",
                    "timestamp", Instant.now().toString()
            );
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ackResponse)));
            }

            String json = objectMapper.writeValueAsString(chatMessage);
            rabbitMQSender.sendMessage(chatMessage.getRoomId(), json);

        } catch (Exception e) {
            System.err.println("YOOOO"+e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}