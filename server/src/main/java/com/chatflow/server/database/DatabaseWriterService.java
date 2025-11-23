package com.chatflow.server.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
@DependsOn("schemaManager") // Wait for schema to be initialized
public class DatabaseWriterService {

    private final MessageRepository repository;
    private final ObjectMapper objectMapper;
    private BlockingQueue<String> writeBuffer;
    private ExecutorService writerExecutor;

    @Value("${database.writer.threads:10}")
    private int writerThreads;

    @Value("${database.batch.size:1000}")
    private int batchSize;

    @Value("${database.flush.interval.ms:500}")
    private long flushIntervalMs;

    @Value("${database.buffer.size:10000}")
    private int bufferSize;

    private volatile boolean running = true;

    public DatabaseWriterService(MessageRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        // Initialize with config values
        this.writeBuffer = new ArrayBlockingQueue<>(bufferSize);
        this.writerExecutor = Executors.newFixedThreadPool(writerThreads);

        System.out.println("🚀 Starting " + writerThreads + " database writer threads");
        System.out.println("   Batch size: " + batchSize);
        System.out.println("   Flush interval: " + flushIntervalMs + "ms");
        System.out.println("   Buffer size: " + bufferSize);

        for (int i = 0; i < writerThreads; i++) {
            final int writerId = i + 1;
            writerExecutor.submit(() -> writerLoop(writerId));
        }
    }

    private void writerLoop(int writerId) {
        List<PersistentMessage> batch = new ArrayList<>(batchSize);
        long lastFlushTime = System.currentTimeMillis();

        while (running || !writeBuffer.isEmpty()) {
            try {
                // Drain available messages
                List<String> jsonMessages = new ArrayList<>();
                writeBuffer.drainTo(jsonMessages, batchSize - batch.size());

                // Parse JSON to PersistentMessage
                for (String json : jsonMessages) {
                    try {
                        JsonNode node = objectMapper.readTree(json);
                        PersistentMessage msg = new PersistentMessage(
                                node.get("messageId").asText(),
                                node.get("roomId").asText(),
                                node.get("userId").asText(),
                                node.get("username").asText(),
                                node.get("message").asText(),
                                node.get("messageType").asText(),
                                java.time.Instant.parse(node.get("timestamp").asText()),
                                node.has("serverId") ? node.get("serverId").asText() : null,
                                node.has("clientIp") ? node.get("clientIp").asText() : null
                        );
                        batch.add(msg);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to parse message: " + e.getMessage());
                    }
                }

                long now = System.currentTimeMillis();
                long timeSinceFlush = now - lastFlushTime;

                // Flush if batch full or timeout reached
                boolean shouldFlush = batch.size() >= batchSize ||
                        (timeSinceFlush >= flushIntervalMs && !batch.isEmpty());

                if (shouldFlush) {
                    int inserted = repository.batchInsert(batch);

                    if (inserted > 0 && inserted % 10000 < batchSize) {
                        System.out.println(String.format(
                                "Writer %d: ✓ %d messages (buffer: %d, latency: %dms)",
                                writerId, inserted, writeBuffer.size(), timeSinceFlush
                        ));
                    }

                    batch.clear();
                    lastFlushTime = now;
                }

                // Small sleep if buffer empty
                if (writeBuffer.isEmpty()) {
                    Thread.sleep(10);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Writer " + writerId + " error: " + e.getMessage());
            }
        }

        // Final flush on shutdown
        if (!batch.isEmpty()) {
            try {
                int inserted = repository.batchInsert(batch);
                System.out.println("Writer " + writerId + " final flush: " + inserted + " messages");
            } catch (Exception e) {
                System.err.println("Writer " + writerId + " final flush failed: " + e.getMessage());
            }
        }

        System.out.println("Database writer " + writerId + " stopped");
    }

    public boolean enqueue(String messageJson) {
        try {
            return writeBuffer.offer(messageJson, 100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public int getBufferSize() {
        return writeBuffer.size();
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("🛑 Shutting down database writers...");
        running = false;

        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
        }

        repository.printStats();
        System.out.println("✓ Database writers stopped");
    }
}