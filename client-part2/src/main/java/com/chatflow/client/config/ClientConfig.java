package com.chatflow.client.config;

import com.chatflow.client.model.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Configuration
public class ClientConfig {

    @Value("${server.url:ws://localhost:8080/chat}")
    private String serverUrl;

    @Value("${total.messages:500000}")
    private int totalMessages;

    @Value("${optimal.threads:150}")
    private int optimalThreads;

    @Value("${queue.capacity:200000}")
    private int queueCapacity;

    @Bean
    public BlockingQueue<ChatMessage> messageQueue() {
        // Large enough to buffer messages
        return new LinkedBlockingQueue<>(queueCapacity);
    }

    @Bean
    public ExecutorService executorService() {
        // Fixed thread pool for predictable resource usage
        return Executors.newFixedThreadPool(optimalThreads);
    }

    @Bean
    public WebSocketClient webSocketClient() {
        return new StandardWebSocketClient();
    }
}