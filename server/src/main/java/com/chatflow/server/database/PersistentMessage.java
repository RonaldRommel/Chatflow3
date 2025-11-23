package com.chatflow.server.database;

import java.time.Instant;
import java.util.UUID;

public class PersistentMessage {
    private final UUID messageId;
    private final String roomId;
    private final int userId;
    private final String username;
    private final String message;
    private final String messageType;
    private final Instant timestamp;
    private final String serverId;
    private final String clientIp;

    public PersistentMessage(String messageId, String roomId, String userId, String username,
                             String message, String messageType, Instant timestamp,
                             String serverId, String clientIp) {
        this.messageId = UUID.fromString(messageId);
        this.roomId = roomId;
        this.userId = Integer.parseInt(userId);
        this.username = username;
        this.message = message;
        this.messageType = messageType;
        this.timestamp = timestamp;
        this.serverId = serverId;
        this.clientIp = clientIp;
    }

    // Getters
    public UUID getMessageId() { return messageId; }
    public String getRoomId() { return roomId; }
    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getMessage() { return message; }
    public String getMessageType() { return messageType; }
    public Instant getTimestamp() { return timestamp; }
    public String getServerId() { return serverId; }
    public String getClientIp() { return clientIp; }
}