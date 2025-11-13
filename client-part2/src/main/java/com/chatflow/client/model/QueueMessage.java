package com.chatflow.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class QueueMessage {

    @JsonProperty("messageId")
    private String messageID;

    @NotNull
    @Size(min = 1, max = 6)
    @Pattern(regexp = "^[1-9][0-9]*$", message = "UserId must contain only digits and not start with 0")
    @JsonProperty("userId")
    private String userID;

    @NotNull
    @Pattern(regexp = "^[a-zA-Z0-9]{3,20}$", message = "Username must be 3-20 alphanumeric characters")
    private String username;

    @NotNull
    @Size(min = 1, max = 500)
    private String message;

    @NotNull
    private Instant timestamp;

    @NotNull
    private MessageType messageType;

    @NotNull
    @JsonProperty("roomId")
    private String roomID;

    private String serverId;
    private String clientIp;

    public QueueMessage() {}

    // Getters and setters
    public String getMessageID() { return messageID; }
    public void setMessageID(String messageID) { this.messageID = messageID; }

    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }

    public String getRoomID() { return roomID; }
    public void setRoomID(String roomID) { this.roomID = roomID; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
}