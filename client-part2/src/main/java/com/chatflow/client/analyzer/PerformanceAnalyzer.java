package com.chatflow.client.analyzer;

import com.chatflow.client.model.MessageMetrics;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PerformanceAnalyzer {

    public void analyzeAndPrint(List<MessageMetrics> metrics, long totalTimeMs) {
        // Filter successful messages only
        List<Long> latencies = metrics.stream()
                .filter(m -> m.getStatusCode() == 200)
                .map(MessageMetrics::getLatency)
                .sorted()
                .collect(Collectors.toList());

        if (latencies.isEmpty()) {
            System.out.println("No successful messages!");
            return;
        }

        int size = latencies.size();
        double mean = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long median = latencies.get(size / 2);
        long p95 = latencies.get((int) (size * 0.95));
        long p99 = latencies.get((int) (size * 0.99));
        long min = latencies.get(0);
        long max = latencies.get(size - 1);

        double throughput = (double) size / (totalTimeMs / 1000.0);

        System.out.println("Successful messages: " + size);
        System.out.println("Failed messages: " + (metrics.size() - size));
        System.out.println("Total runtime: " + (totalTimeMs / 1000.0) + " seconds");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " msg/sec");
        System.out.println("\n=== Latency Statistics (ms) ===");
        System.out.println("Mean: " + String.format("%.2f", mean));
        System.out.println("Median: " + median);
        System.out.println("95th percentile: " + p95);
        System.out.println("99th percentile: " + p99);
        System.out.println("Min: " + min);
        System.out.println("Max: " + max);

        // Per-room analysis
        Map<String, Long> perRoom = metrics.stream()
                .collect(Collectors.groupingBy(
                        MessageMetrics::getRoomId,
                        Collectors.counting()
                ));

        System.out.println("\n=== Per-Room Distribution ===");
        perRoom.forEach((room, count) ->
                System.out.println("Room " + room + ": " + count + " messages")
        );

        // Message type distribution
        Map<String, Long> perType = metrics.stream()
                .collect(Collectors.groupingBy(
                        MessageMetrics::getMessageType,
                        Collectors.counting()
                ));

        System.out.println("\n=== Message Type Distribution ===");
        perType.forEach((type, count) ->
                System.out.println(type + ": " + count + " messages")
        );
    }
}