package com.chatflow.server.rabbit;

import com.chatflow.server.database.DatabaseWriterService;
import com.chatflow.server.handler.SessionManager;
import com.rabbitmq.client.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@DependsOn("rabbitMQInitializer")
public class RabbitMQConsumer {

    private static final String EXCHANGE_PREFIX = "chat.exchange.";
    private static final int ROOM_COUNT = 20;
    private static final int CONSUMERS_PER_ROOM = 1;

    private final ChannelPool channelPool;
    private final ExecutorService consumerExecutor = Executors.newFixedThreadPool(ROOM_COUNT * CONSUMERS_PER_ROOM);
    private final AtomicInteger processed = new AtomicInteger(0);
    private final SessionManager sessionManager;
    private final DatabaseWriterService databaseWriter; // NEW

    public RabbitMQConsumer(@Qualifier("consumerPool") ChannelPool channelPool,
                            SessionManager sessionManager,
                            DatabaseWriterService databaseWriter) throws Exception { // NEW
        this.channelPool = channelPool;
        this.sessionManager = sessionManager;
        this.databaseWriter = databaseWriter; // NEW
        startConsumers();
    }

    private void startConsumers() throws Exception {
        String serverId = InetAddress.getLocalHost().getHostName();
        int totalConsumers = ROOM_COUNT * CONSUMERS_PER_ROOM;
        System.out.println("Starting " + totalConsumers + " consumers (" + CONSUMERS_PER_ROOM + " per room)");

        for (int i = 1; i <= ROOM_COUNT; i++) {
            final String roomId = "room" + i;
            final String exchangeName = EXCHANGE_PREFIX + roomId;
            final String queueName = "queue_" + serverId + "_" + roomId;

            for (int j = 0; j < CONSUMERS_PER_ROOM; j++) {
                final int consumerIndex = j;

                consumerExecutor.submit(() -> {
                    try {
                        Channel channel = channelPool.borrowChannel();

                        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true);
                        channel.queueDeclare(queueName, true, false, false, null);
                        channel.queueBind(queueName, exchangeName, "");

                        if (consumerIndex == 0) {
                            System.out.println(CONSUMERS_PER_ROOM + " consumers for " + roomId);
                        }

                        channel.basicConsume(queueName, false, new DefaultConsumer(channel) {
                            @Override
                            public void handleDelivery(String consumerTag, Envelope envelope,
                                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                                try {
                                    String message = new String(body, StandardCharsets.UTF_8);

                                    // 1. Broadcast to WebSocket (real-time - keep this fast!)
                                    broadcast(roomId, message);

                                    // 2. Enqueue for database writing (asynchronous - doesn't block)
                                    boolean enqueued = databaseWriter.enqueue(message);
                                    if (!enqueued) {
                                        System.err.println("⚠️ DB write buffer full! Message may be lost.");
                                        // TODO: Send to dead letter queue
                                    }

                                    // 3. Acknowledge to RabbitMQ
                                    getChannel().basicAck(envelope.getDeliveryTag(), false);

                                    int count = processed.incrementAndGet();
                                    if (count % 100000 == 0) {
                                        System.out.println("Processed: " + count +
                                                ", DB buffer: " + databaseWriter.getBufferSize());
                                    }

                                } catch (Exception e) {
                                    System.err.println("Error processing message: " + e.getMessage());
                                    try {
                                        getChannel().basicNack(envelope.getDeliveryTag(), false, false);
                                    } catch (IOException ioException) {}
                                }
                            }
                        });

                    } catch (Exception e) {
                        System.err.println("Failed consumer for " + roomId + ": " + e.getMessage());
                    }
                });
            }
        }

        System.out.println("All consumers started");
    }

    private void broadcast(String roomId, String message) throws IOException {
        WebSocketSession session = sessionManager.getSession(roomId);
        if (session != null) {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        }
    }
}