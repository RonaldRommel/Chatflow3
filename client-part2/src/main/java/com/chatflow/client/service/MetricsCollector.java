package com.chatflow.client.service;

import com.chatflow.client.model.MessageMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsCollector {

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    // Bounded queue to prevent memory issues
    private final BlockingQueue<MessageMetrics> writeQueue = new LinkedBlockingQueue<>(50000);
    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean isRunning = true;

    private PrintWriter fileWriter;
    private final String filename = "results/metrics.csv";

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get("results"));
        fileWriter = new PrintWriter(new BufferedWriter(new FileWriter(filename), 8192 * 4)); // 32KB buffer
        fileWriter.println("timestamp,messageType,latency,statusCode,roomId");

        // Start batch writer thread
        writerExecutor.submit(this::batchWriter);
    }

    public void record(MessageMetrics metrics) {
        // Update counters immediately
        if (metrics.getStatusCode() == 200) {
            successCount.incrementAndGet();
        } else {
            failureCount.incrementAndGet();
        }

        // Non-blocking offer - drop if queue is full (or use put() to block)
        if (!writeQueue.offer(metrics)) {
            System.err.println("⚠️ Write queue full! Metrics dropped.");
        }
    }

    /**
     * Batch writer - drains queue and writes in batches
     */
    private void batchWriter() {
        List<MessageMetrics> batch = new ArrayList<>(1000);

        try {
            while (isRunning || !writeQueue.isEmpty()) {
                // Drain up to 1000 metrics at once
                writeQueue.drainTo(batch, 1000);

                if (!batch.isEmpty()) {
                    for (MessageMetrics m : batch) {
                        fileWriter.printf("%d,%s,%d,%d,%s%n",
                                m.getSendTimestamp().toEpochMilli(),
                                m.getMessageType(),
                                m.getLatency(),
                                m.getStatusCode(),
                                m.getRoomId()
                        );
                    }
                    fileWriter.flush();
                    batch.clear();
                } else {
                    // No data, sleep briefly
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("Batch writer stopped. Queue size: " + writeQueue.size());
        }
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public int getPendingWrites() {
        return writeQueue.size();
    }

    /**
     * Read all metrics from file for analysis
     */
    public List<MessageMetrics> getAllMetrics() throws IOException {
        List<MessageMetrics> metrics = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filename))) {
            reader.readLine(); // Skip header

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");

                MessageMetrics metric = new MessageMetrics();
                metric.setSendTimestamp(Instant.ofEpochMilli(Long.parseLong(parts[0])));
                metric.setMessageType(parts[1]);
                metric.setReceiveTimestamp(Instant.ofEpochMilli(
                        Long.parseLong(parts[0]) + Long.parseLong(parts[2])
                ));
                metric.setStatusCode(Integer.parseInt(parts[3]));
                metric.setRoomId(parts[4]);

                metrics.add(metric);
            }
        }

        return metrics;
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("Shutting down metrics collector...");
        isRunning = false;

        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("⚠️ Writer didn't finish in time!");
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (fileWriter != null) {
            fileWriter.close();
        }

        System.out.println("✅ Metrics written to " + filename);
    }
}