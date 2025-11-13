package com.chatflow.server.rabbit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

@Configuration
public class RabbitMQConfig {
    @Value("${rabbitmq.host}")
    private String host;

    @Value("${rabbitmq.port:5672}")
    private int port;

    @Value("${rabbitmq.username}")
    private String username;

    @Value("${rabbitmq.password}")
    private String password;

    @Value("${rabbitmq.producer.pool.size:50}")
    private int producerPoolSize;

    @Value("${rabbitmq.consumer.pool.size:150}")
    private int consumerPoolSize;

    @Bean
    @Qualifier("producerPool")
    public ChannelPool producerChannelPool() throws IOException, TimeoutException {
        return new ChannelPool(producerPoolSize,host,port,username,password);
    }

    @Bean
    @Qualifier("consumerPool")
    public ChannelPool consumerChannelPool() throws IOException, TimeoutException {
        return new ChannelPool(consumerPoolSize,host,port,username,password);
    }
}