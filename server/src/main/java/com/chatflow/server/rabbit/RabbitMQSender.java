package com.chatflow.server.rabbit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@DependsOn("rabbitMQInitializer")
public class RabbitMQSender {

    private static final String EXCHANGE_PREFIX = "chat.exchange.";
    private final ChannelPool channelPool;
    private final AtomicInteger sentCount = new AtomicInteger(0);

    public RabbitMQSender(@Qualifier("producerPool") ChannelPool channelPool) {
        this.channelPool = channelPool;
    }

    public void sendMessage(String roomId, String message) {
        Channel channel = null;
        try {
            channel = channelPool.borrowChannel();

            if (channel == null) {
                System.err.println("❌ ERROR: borrowChannel() returned NULL!");
                return;
            }

            if (!channel.isOpen()) {
                System.err.println("❌ ERROR: Channel is CLOSED! RoomId: " + roomId);
                return;
            }


            String exchangeName = EXCHANGE_PREFIX + roomId;
//            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
//                    .deliveryMode(1) // 1 = transient (memory only)
//                    .build();

            if (sentCount.get() < 5) {
                System.out.println("📤 Publishing to exchange: " + exchangeName +
                        ", Channel: " + channel.getChannelNumber() +
                        ", Message length: " + message.length());
            }
            channel.basicPublish(exchangeName, "", null, message.getBytes());

            int count = sentCount.incrementAndGet();
            if (count % 100000 == 0) {
                System.out.println("Published: " + count);
            }
        } catch (Exception e) {
            System.err.println("Failed to publish: " + e.getMessage());
        } finally {
            if (channel != null) {
                channelPool.returnChannel(channel);
            }
        }
    }
}