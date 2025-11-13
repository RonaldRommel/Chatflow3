package com.chatflow.client.service;

import com.chatflow.client.model.MessageMetrics;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsCollector {

    private final ConcurrentLinkedQueue<MessageMetrics> allMetrics = new ConcurrentLinkedQueue<>();
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    public void record(MessageMetrics metrics) {
        allMetrics.add(metrics);

        if (metrics.getStatusCode() == 200) {
            successCount.incrementAndGet();
        } else {
            failureCount.incrementAndGet();
        }
    }

    public List<MessageMetrics> getAllMetrics() {
        return new ArrayList<>(allMetrics);
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public void writeToCSV(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("timestamp,messageType,latency,statusCode,roomId");

            for (MessageMetrics m : allMetrics) {
                writer.printf("%d,%s,%d,%d,%s%n",
                        m.getSendTimestamp().toEpochMilli(),
                        m.getMessageType(),
                        m.getLatency(),
                        m.getStatusCode(),
                        m.getRoomId()
                );
            }
        }
        System.out.println("Metrics exported to " + filename);
    }
}