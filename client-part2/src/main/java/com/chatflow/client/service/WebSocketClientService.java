package com.chatflow.client.service;

import com.chatflow.client.model.ChatMessage;
import com.chatflow.client.model.MessageMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class WebSocketClientService {

    @Value("${server.url}")
    private String serverUrl;

    @Autowired
    private WebSocketClient webSocketClient;

    @Autowired
    private MetricsCollector metricsCollector;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, WebSocketSession> connectionPool = new ConcurrentHashMap<>();
    private final AtomicInteger reconnectionCount = new AtomicInteger(0);

    // Track pending responses using messageId from ChatMessage
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();

    public WebSocketClientService(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @PostConstruct
    public void init() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Asynchronously sends a message and returns a CompletableFuture that completes when response arrives
     */
    public CompletableFuture<Void> sendMessageAsync(ChatMessage message) {
        return sendMessageWithRetry(message, 0, 5);
    }

    private CompletableFuture<Void> sendMessageWithRetry(ChatMessage message, int retries, int maxRetries) {
        try {
            message.setTimestamp(Instant.now());
            WebSocketSession session = getOrCreateConnection(message.getRoomId());
            String messageId = message.getMessageId();
            CompletableFuture<String> responseFuture = new CompletableFuture<>();
            pendingResponses.put(messageId, responseFuture);
            session.sendMessage(new TextMessage(messageToJson(message)));

            return responseFuture
                    .orTimeout(20, TimeUnit.SECONDS)
                    .thenAccept(response -> {
                    })
                    .exceptionallyCompose(ex -> {
                        if (retries < maxRetries) {
                            long backoffMs = (long) Math.pow(2, retries + 1) * 100;

                            // Clean and readable!
                            return delayedFuture(
                                    backoffMs,
                                    TimeUnit.MILLISECONDS,
                                    () -> sendMessageWithRetry(message, retries + 1, maxRetries)
                            );

                        } else {
                            MessageMetrics metrics = new MessageMetrics();
                            metrics.setSendTimestamp(message.getTimestamp());
                            metrics.setMessageType(message.getMessageType());
                            metrics.setRoomId(message.getRoomId());
                            metrics.setReceiveTimestamp(Instant.now());
                            metrics.setStatusCode(500);
                            metricsCollector.record(metrics);
                            System.err.println("Failed message " + messageId + ": " + ex.getMessage());
                            return CompletableFuture.completedFuture(null);
                        }
                    })
                    .whenComplete((r, ex) -> pendingResponses.remove(messageId));

        } catch (Exception e) {
            if (retries < maxRetries) {
                long backoffMs = (long) Math.pow(2, retries + 1) * 100;
                return delayedFuture(
                        backoffMs,
                        TimeUnit.MILLISECONDS,
                        () -> sendMessageWithRetry(message, retries + 1, maxRetries)
                );
            } else {
                MessageMetrics metrics = new MessageMetrics();
                metrics.setSendTimestamp(message.getTimestamp());
                metrics.setMessageType(message.getMessageType());
                metrics.setRoomId(message.getRoomId());
                metrics.setReceiveTimestamp(Instant.now());
                metrics.setStatusCode(500);
                metricsCollector.record(metrics);
                return CompletableFuture.completedFuture(null);
            }
        }
    }

    public int getPendingResponsesCount() {
        return pendingResponses.size();
    }

    /**
     * Returns a CompletableFuture that completes after a delay
     */
    private <T> CompletableFuture<T> delayedFuture(long delay, TimeUnit unit, Supplier<CompletableFuture<T>> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();

        scheduler.schedule(() -> {
            supplier.get().whenComplete((value, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(value);
                }
            });
        }, delay, unit);

        return result;
    }


    public WebSocketSession getOrCreateConnection(String roomId) throws Exception {
        return connectionPool.computeIfAbsent(roomId, rid -> {
            try {
                String url = serverUrl + "/" + rid;

                WebSocketHandler handler = new TextWebSocketHandler() {

                    @Override
                    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                        System.out.println("✓ Connected to room: " + rid);
                    }

                    @Override
                    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                        String response = message.getPayload();
                        try {
                            JsonNode jsonNode = objectMapper.readTree(response);
                            if (jsonNode.has("status")) {
                                handleAckMessage(jsonNode,response);
                            } else {
                                handleBroadCastMessage(message);
                            }
                        } catch (Exception e) {
                            System.err.println("Error handling message: " + e.getMessage());
                        }
                    }

                    @Override
                    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                        System.err.println("✗ Transport error in room " + rid + ": " + exception.getMessage());

                        // Fail all pending responses for this room
                        pendingResponses.keySet().stream()
                                .filter(key -> {
                                    // Since messageId is UUID, we need another way to associate with room
                                    // For now, fail ALL pending responses when any connection fails
                                    return true;
                                })
                                .forEach(key -> {
                                    CompletableFuture<String> future = pendingResponses.remove(key);
                                    if (future != null && !future.isDone()) {
                                        future.completeExceptionally(exception);
                                    }
                                });

                        // Remove failed connection from pool
                        connectionPool.remove(rid);
                    }

                    @Override
                    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                        System.out.println("Connection closed for room " + rid + ": " + status);

                        // Fail all pending responses for this room
                        pendingResponses.keySet().stream()
                                .forEach(key -> {
                                    CompletableFuture<String> future = pendingResponses.remove(key);
                                    if (future != null && !future.isDone()) {
                                        future.completeExceptionally(new Exception("Connection closed"));
                                    }
                                });

                        connectionPool.remove(rid);
                    }
                };

                WebSocketSession session = webSocketClient.execute(handler, url).get();
                return session;

            } catch (Exception e) {
                reconnectionCount.incrementAndGet();
                throw new RuntimeException("Failed to connect to " + rid, e);
            }
        });
    }

    private void handleAckMessage(JsonNode jsonNode, String response) {
        String messageId = jsonNode.get("messageId").asText();
        if (messageId != null) {
            CompletableFuture<String> future = pendingResponses.get(messageId);
            if (future != null) {
                future.complete(response);
            } else {
                System.err.println("Received response for unknown messageId: " + messageId);
            }
        } else {
            System.err.println("Received response without messageId: " + response);
        }
    }

    private void handleBroadCastMessage(TextMessage message) throws JsonProcessingException {
        ChatMessage msg = objectMapper.readValue(message.getPayload(),ChatMessage.class);
        MessageMetrics metrics = new MessageMetrics();
        metrics.setSendTimestamp(msg.getTimestamp());
        metrics.setMessageType(msg.getMessageType());
        metrics.setRoomId(msg.getRoomId());
        metrics.setReceiveTimestamp(Instant.now());
        metrics.setStatusCode(200);
        metricsCollector.record(metrics);
    }

    /**
     * Extract messageId from server response JSON using Jackson
     */
    private String extractMessageIdFromResponse(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode messageIdNode = rootNode.get("messageId");

            if (messageIdNode != null) {
                return messageIdNode.asText();
            }
        } catch (Exception e) {
            System.err.println("Failed to parse JSON response: " + e.getMessage());
        }
        return null;
    }

    /**
     * Convert ChatMessage to JSON using Jackson
     * Excludes roomId from JSON payload (only used for routing)
     */
    private String messageToJson(ChatMessage msg) {
        try {
            // Create a copy without roomId for sending to server
            // Since roomId is in the URL path, we don't need it in the JSON body
            // But we'll include messageId for response tracking
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            System.err.println("Failed to serialize message: " + e.getMessage());
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    public int getReconnectionCount() {
        return reconnectionCount.get();
    }

    public int getConnectionPoolSize() {
        return connectionPool.size();
    }

    @PreDestroy
    public void cleanup() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}