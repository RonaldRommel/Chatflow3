package com.chatflow.client;

import com.chatflow.client.analyzer.ChartGenerator;
import com.chatflow.client.analyzer.PerformanceAnalyzer;
import com.chatflow.client.service.MessageGenerator;
import com.chatflow.client.service.MetricsCollector;
import com.chatflow.client.service.WebSocketClientService;
import com.chatflow.client.worker.MessageSenderWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@SpringBootApplication
public class ClientApplication implements CommandLineRunner {

    @Autowired
    private MessageGenerator messageGenerator;

    @Autowired
    private WebSocketClientService clientService;

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private PerformanceAnalyzer analyzer;

    @Autowired
    private ChartGenerator chartGenerator;

    @Autowired
    private ExecutorService executorService;

    @Value("${optimal.threads}")
    private int optimalThreads; // e.g., 100-200 threads

    @Value("${total.messages}")
    private int totalMessages; // 500,000

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting load test with " + totalMessages + " messages...");
        System.out.println("Using " + optimalThreads + " worker threads");

        long startTime = System.currentTimeMillis();
        for  (int i = 1; i <= 20; i++) {
            clientService.getOrCreateConnection("room"+i);
        }
        // 1. Start message generator (runs in background)
        messageGenerator.startGenerating(totalMessages);

        // 2. Create worker threads - NO per-thread message count!
        List<Future<?>> futures = new ArrayList<>();
        System.out.println("Starting workers");
        for (int i = 0; i < optimalThreads; i++) {
            MessageSenderWorker worker = new MessageSenderWorker(
                    messageGenerator.getMessageQueue(),
                    clientService,
                    metricsCollector
            );
            futures.add(executorService.submit(worker));
        }

        System.out.println("✅ Submitted " + futures.size() + " workers to executor");
        System.out.println("⏳ Waiting for workers to complete...");
        System.out.println("Active threads: " + Thread.activeCount());
        System.out.println();

        // 3. Monitor progress
        int lastCompleted = 0;
        int checkCount = 0;
        while (true) {
            Thread.sleep(2000); // Check every 2 seconds

            int completed = 0;
            for (Future<?> future : futures) {
                if (future.isDone()) {
                    completed++;
                }
            }

            int sent = metricsCollector.getSuccessCount() + metricsCollector.getFailureCount();
            int queueSize = messageGenerator.getMessageQueue().size();

            System.out.println(String.format(
                    "[%ds] Workers: %d/%d done | Messages: %d/%d sent | Queue: %d | Message generated %d",
                    (checkCount * 2),
                    completed,
                    futures.size(),
                    sent,
                    totalMessages,
                    queueSize,
                    messageGenerator.getMessagesGenerated()
            ));

            if (completed == futures.size()) {
                System.out.println("\n✅ All workers completed!");
                break;
            }
        }


        // 3. Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get(); // Blocks until thread finishes
        }

        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // 4. Generate reports
        System.out.println("\n=== Load Test Complete ===");
        System.out.println("Total connections created: " + clientService.getConnectionPoolSize());// gotta chnge
        System.out.println("Reconnections: " + clientService.getReconnectionCount());

        System.out.println("Waiting for metrics to finish writing...");
        while (metricsCollector.getPendingWrites() > 0) {
            System.out.println("Pending writes: " + metricsCollector.getPendingWrites());
            Thread.sleep(1000);
        }

        analyzer.analyzeAndPrint(metricsCollector.getAllMetrics(), totalTime);
        chartGenerator.generateThroughputChart(metricsCollector.getAllMetrics());

        System.exit(0);
    }
}