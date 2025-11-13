package com.chatflow.client.model;

import lombok.Data;

import java.time.Instant;

@Data
public class MessageMetrics {
    private Instant sendTimestamp;
    private Instant receiveTimestamp;
    private String messageType;
    private int statusCode;
    private String roomId;

    public long getLatency() {
        return (receiveTimestamp.toEpochMilli() - sendTimestamp.toEpochMilli());
    }
}