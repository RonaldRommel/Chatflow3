package com.chatflow.client.service;

import com.chatflow.client.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;

@Service
public class MessageGenerator {

    private final BlockingQueue<ChatMessage> messageQueue;
    private final Random random = new Random();
    private List<String> messagePhrases;
    private int messagesGenerated;

    public MessageGenerator(BlockingQueue<ChatMessage> messageQueue) {
        this.messageQueue = messageQueue;
        this.messagesGenerated=0;
    }

    @PostConstruct
    public void init() {
        // Load 50 pre-defined messages
        messagePhrases = Arrays.asList(
                "Hello everyone!","How's the project going?","Meeting at 3pm","Did anyone see the update from the client?","Good morning!",
                "Please review the latest PR","Lunch break soon?","Can someone help me with this bug?","Thanks for the clarification","I’ll be out for an hour",
                "Reminder: stand-up in 10 minutes","Any updates on the server issue?","Congrats on finishing the task!","Let’s sync after the call","Please check your emails",
                "I’m stuck on this feature","Great work on the presentation","Can we push the deadline?","I’m testing the new build","Who is online now?",
                "Let’s brainstorm ideas","The meeting notes are uploaded","Please assign me that task","Did anyone check the logs?","I need access to the repo",
                "Can we have a quick call?","FYI: system maintenance tonight","The client approved the changes","Happy Friday!","Any blockers?",
                "I’ll take care of that","Thanks for the help earlier","Please update the documentation","Can someone review my code?","Reminder: submit timesheets",
                "The deployment was successful","Can we postpone the meeting?","Who will take this ticket?","I’ll work on this after lunch","Please prioritize the bug fix",
                "Great job, team!","I’m running tests now","Can you explain this part?","Please share your screen","The design looks good",
                "I’ll be late to the meeting","Can we split this task?","I have completed my part","Let’s do a quick update","The server is down",
                "Thanks everyone!"
        );

    }

    public void startGenerating(int totalMessages) throws InterruptedException {
        Thread generator = new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                for (int i = 0; i < totalMessages; i++) {
                    messageQueue.put(generateMessage());
                    messagesGenerated++;
//                    System.out.println("Generating " + messageQueue.size() + " messages");
                    // Optional: Log progress every 50k messages
                    if ((i + 1) % 50000 == 0) {
                        System.out.println("Generated " + (i + 1) + " messages");
                    }
                }
                long endTime = System.currentTimeMillis();

                System.out.println("Message generation complete!"+ (endTime-startTime));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "MessageGenerator");
        generator.start();
//        generator.setDaemon(true);

    }


    private ChatMessage generateMessage() {
        String userId = String.valueOf( random.nextInt(100000) + 1);
        String username = "user" + userId;
        String message = messagePhrases.get(random.nextInt(messagePhrases.size()));
        String roomId = "room"+(random.nextInt(20) + 1);
        Instant timestamp = Instant.now();

        // 90% TEXT, 5% JOIN, 5% LEAVE
        MessageType messageType;
        int typeRand = random.nextInt(100);
        if (typeRand < 90) {
            messageType = MessageType.TEXT;
        } else if (typeRand < 95) {
            messageType = MessageType.JOIN;
        } else {
            messageType = MessageType.LEAVE;
        }

        return new ChatMessage(userId, username, message, roomId, messageType, timestamp );
    }

    public int getMessagesGenerated() {
        return messagesGenerated;
    }

    public BlockingQueue<ChatMessage> getMessageQueue() {
        return messageQueue;
    }
}