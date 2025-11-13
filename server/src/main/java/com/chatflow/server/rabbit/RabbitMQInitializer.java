package com.chatflow.server.rabbit;

import com.rabbitmq.client.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQInitializer {

    private static final String EXCHANGE_PREFIX = "chat.exchange.";
    private static final int ROOM_COUNT = 20;
    private ChannelPool channelPool;
    public RabbitMQInitializer(@Qualifier("producerPool") ChannelPool channelPool) {
        this.channelPool = channelPool;
        try {
            Channel channel = channelPool.borrowChannel();
            for (int i = 1; i <= ROOM_COUNT; i++) {
                String exchangeName = EXCHANGE_PREFIX + "room" + i;
                channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true);
            }

            channelPool.returnChannel(channel);
            System.out.println("RabbitMQ initialized: 20 exchanges created");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RabbitMQ", e);
        }
    }

    @PreDestroy
    public void cleanupExchanges() {
        try {
            Channel channel = channelPool.borrowChannel();
            for (int i = 1; i <= ROOM_COUNT; i++) {
                String exchangeName = EXCHANGE_PREFIX + "room" + i;
                channel.exchangeDelete(exchangeName);
            }
            channelPool.returnChannel(channel);
            System.out.println("RabbitMQ exchanges deleted on shutdown.");
        } catch (Exception e) {
            System.err.println("Error cleaning up RabbitMQ exchanges: " + e.getMessage());
        }
    }
}