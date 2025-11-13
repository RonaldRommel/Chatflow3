package com.chatflow.client.worker;

import com.chatflow.client.model.ChatMessage;
import com.chatflow.client.model.MessageMetrics;
import com.chatflow.client.service.MetricsCollector;
import com.chatflow.client.service.WebSocketClientService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageSenderWorker implements Runnable {

    private final BlockingQueue<ChatMessage> messageQueue;
    private final WebSocketClientService clientService;
    private final MetricsCollector metricsCollector;
    private final AtomicInteger messagesSent;

    public MessageSenderWorker(
            BlockingQueue<ChatMessage> messageQueue,
            WebSocketClientService clientService,
            MetricsCollector metricsCollector) {
        this.messageQueue = messageQueue;
        this.clientService = clientService;
        this.metricsCollector = metricsCollector;
        this.messagesSent = new AtomicInteger(0);
    }

    @Override
    public void run() {
        List<CompletableFuture<Void>> pendingFutures = new ArrayList<>();

        try {
            while (true) {
                // Poll message from queue
                ChatMessage message = messageQueue.poll(3, TimeUnit.SECONDS);

                // Queue is empty - we're done polling
                if (message == null) {
                    break;
                }


                // Send message asynchronously
                CompletableFuture<Void> future = clientService.sendMessageAsync(message)
                        .thenRun(() -> {
                            messagesSent.incrementAndGet();
                        });

                pendingFutures.add(future);

                // Optional: Limit concurrent requests per thread to avoid overwhelming
//                if (pendingFutures.size() >= 100) {
//                    // Wait for some to complete
//                    CompletableFuture.allOf(pendingFutures.toArray(new CompletableFuture[0])).join();
//                    pendingFutures.clear();
//
//                }
            }

            // Wait for all remaining futures to complete
            CompletableFuture.allOf(pendingFutures.toArray(new CompletableFuture[0])).join();

//            System.out.println(Thread.currentThread().getName() +
//                    " completed. Sent " + messagesSent.get() + " messages.");

        } catch (Exception e) {
            System.err.println("Worker error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int getMessagesSent() {
        return messagesSent.get();
    }
}