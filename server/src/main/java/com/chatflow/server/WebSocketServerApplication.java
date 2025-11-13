package com.chatflow.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;

@SpringBootApplication(exclude = {RabbitAutoConfiguration.class})
public class WebSocketServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebSocketServerApplication.class, args);
    }
}