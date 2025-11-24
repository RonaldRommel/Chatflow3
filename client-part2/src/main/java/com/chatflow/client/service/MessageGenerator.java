package com.chatflow.client.service;

import com.chatflow.client.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageGenerator {

    private final BlockingQueue<ChatMessage> messageQueue;
    private final Random random = new Random();
    private List<String> messagePhrases;
    private int messagesGenerated;

    // Track which users are in which rooms
    private final ConcurrentHashMap<String, Set<String>> roomUsers = new ConcurrentHashMap<>();
    private final Set<String> allUserIds = ConcurrentHashMap.newKeySet();

    private static final int MAX_USERS = 50;
    private static final int ROOM_COUNT = 20;

    public MessageGenerator(BlockingQueue<ChatMessage> messageQueue) {
        this.messageQueue = messageQueue;
        this.messagesGenerated = 0;

        // Initialize room tracking
        for (int i = 1; i <= ROOM_COUNT; i++) {
            roomUsers.put("room" + i, ConcurrentHashMap.newKeySet());
        }
    }

    @PostConstruct
    public void init() {
        messagePhrases = Arrays.asList(
                "Hello everyone!", "How's the project going?", "Meeting at 3pm",
                "Did anyone see the update from the client?", "Good morning!",
                "Please review the latest PR", "Lunch break soon?",
                "Can someone help me with this bug?", "Thanks for the clarification",
                "I'll be out for an hour", "Reminder: stand-up in 10 minutes",
                "Any updates on the server issue?", "Congrats on finishing the task!",
                "Let's sync after the call", "Please check your emails",
                "I'm stuck on this feature", "Great work on the presentation",
                "Can we push the deadline?", "I'm testing the new build",
                "Who is online now?", "Let's brainstorm ideas",
                "The meeting notes are uploaded", "Please assign me that task",
                "Did anyone check the logs?", "I need access to the repo",
                "Can we have a quick call?", "FYI: system maintenance tonight",
                "The client approved the changes", "Happy Friday!", "Any blockers?",
                "I'll take care of that", "Thanks for the help earlier",
                "Please update the documentation", "Can someone review my code?",
                "Reminder: submit timesheets", "The deployment was successful",
                "Can we postpone the meeting?", "Who will take this ticket?",
                "I'll work on this after lunch", "Please prioritize the bug fix",
                "Great job, team!", "I'm running tests now",
                "Can you explain this part?", "Please share your screen",
                "The design looks good", "I'll be late to the meeting",
                "Can we split this task?", "I have completed my part",
                "Let's do a quick update", "The server is down", "Thanks everyone!"
        );
    }

    public void startGenerating(int totalMessages) throws InterruptedException {
        Thread generator = new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Phase 1: Generate JOIN, TEXT, and LEAVE messages (90% TEXT, 5% JOIN, 5% LEAVE)
                int textMessages = (int) (totalMessages * 0.90);
                int joinMessages = (int) (totalMessages * 0.05);
                int leaveMessages = (int) (totalMessages * 0.05);

                // Ensure we have exact count
                int remaining = totalMessages - (textMessages + joinMessages + leaveMessages);
                textMessages += remaining;

                System.out.println("Generating: " + textMessages + " TEXT, " +
                        joinMessages + " JOIN, " + leaveMessages + " LEAVE");

                List<ChatMessage> messages = new ArrayList<>();

                // Generate JOIN messages first to populate rooms
                for (int i = 0; i < joinMessages; i++) {
                    messages.add(generateJoinMessage());
                }

                // Generate TEXT messages (only for users already in rooms)
                for (int i = 0; i < textMessages; i++) {
                    ChatMessage msg = generateTextMessage();
                    if (msg != null) {
                        messages.add(msg);
                    } else {
                        // If no users in rooms yet, generate a JOIN instead
                        messages.add(generateJoinMessage());
                    }
                }

                // Generate LEAVE messages
                for (int i = 0; i < leaveMessages; i++) {
                    ChatMessage msg = generateLeaveMessage();
                    if (msg != null) {
                        messages.add(msg);
                    }
                }

                // Shuffle to distribute message types throughout the test
                Collections.shuffle(messages);

                // Put messages in queue
                for (int i = 0; i < messages.size(); i++) {
                    messageQueue.put(messages.get(i));
                    messagesGenerated++;

                    if ((i + 1) % 50000 == 0) {
                        System.out.println("Queued " + (i + 1) + " messages");
                    }
                }

                // Phase 2: Generate final LEAVE messages for all remaining users
                System.out.println("Generating final LEAVE messages for remaining users...");
                List<ChatMessage> finalLeaves = generateFinalLeaveMessages();
                for (ChatMessage leave : finalLeaves) {
                    messageQueue.put(leave);
                    messagesGenerated++;
                }

                long endTime = System.currentTimeMillis();
                System.out.println("Message generation complete! Total: " + messagesGenerated +
                        " messages in " + (endTime - startTime) + "ms");
                System.out.println("Final state - Total users: " + allUserIds.size());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "MessageGenerator");
        generator.start();
    }

    private ChatMessage generateJoinMessage() {
        // Pick a user (create new if under MAX_USERS)
        String userId;
        if (allUserIds.size() < MAX_USERS) {
            userId = String.valueOf(allUserIds.size() + 1);
            allUserIds.add(userId);
        } else {
            // Reuse existing user
            userId = new ArrayList<>(allUserIds).get(random.nextInt(allUserIds.size()));
        }

        String username = "user" + userId;
        String roomId = "room" + (random.nextInt(ROOM_COUNT) + 1);

        // Add user to room
        roomUsers.get(roomId).add(userId);

        return new ChatMessage(
                userId,
                username,
                username + " joined the room",
                roomId,
                MessageType.JOIN,
                Instant.now()
        );
    }

    private ChatMessage generateTextMessage() {
        // Find a room with users
        List<String> roomsWithUsers = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : roomUsers.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                roomsWithUsers.add(entry.getKey());
            }
        }

        if (roomsWithUsers.isEmpty()) {
            return null; // No users in any room yet
        }

        // Pick a random room with users
        String roomId = roomsWithUsers.get(random.nextInt(roomsWithUsers.size()));
        Set<String> usersInRoom = roomUsers.get(roomId);

        // Pick a random user from that room
        String userId = new ArrayList<>(usersInRoom).get(random.nextInt(usersInRoom.size()));
        String username = "user" + userId;
        String message = messagePhrases.get(random.nextInt(messagePhrases.size()));

        return new ChatMessage(
                userId,
                username,
                message,
                roomId,
                MessageType.TEXT,
                Instant.now()
        );
    }

    private ChatMessage generateLeaveMessage() {
        // Find a room with users
        List<String> roomsWithUsers = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : roomUsers.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                roomsWithUsers.add(entry.getKey());
            }
        }

        if (roomsWithUsers.isEmpty()) {
            return null; // No users to remove
        }

        // Pick a random room with users
        String roomId = roomsWithUsers.get(random.nextInt(roomsWithUsers.size()));
        Set<String> usersInRoom = roomUsers.get(roomId);

        // Pick a random user from that room
        String userId = new ArrayList<>(usersInRoom).get(random.nextInt(usersInRoom.size()));
        String username = "user" + userId;

        // Remove user from room
        usersInRoom.remove(userId);

        return new ChatMessage(
                userId,
                username,
                username + " left the room",
                roomId,
                MessageType.LEAVE,
                Instant.now()
        );
    }

    private List<ChatMessage> generateFinalLeaveMessages() {
        List<ChatMessage> leaveMessages = new ArrayList<>();

        // For each room, make all remaining users leave
        for (Map.Entry<String, Set<String>> entry : roomUsers.entrySet()) {
            String roomId = entry.getKey();
            Set<String> usersInRoom = new HashSet<>(entry.getValue()); // Copy to avoid modification during iteration

            for (String userId : usersInRoom) {
                String username = "user" + userId;

                ChatMessage leave = new ChatMessage(
                        userId,
                        username,
                        username + " left the room",
                        roomId,
                        MessageType.LEAVE,
                        Instant.now()
                );

                leaveMessages.add(leave);
                entry.getValue().remove(userId); // Remove from tracking
            }
        }

        System.out.println("Generated " + leaveMessages.size() + " final LEAVE messages");
        return leaveMessages;
    }

    public int getMessagesGenerated() {
        return messagesGenerated;
    }

    public BlockingQueue<ChatMessage> getMessageQueue() {
        return messageQueue;
    }

    public int getTotalUsersCreated() {
        return allUserIds.size();
    }

    public Map<String, Integer> getRoomUserCounts() {
        Map<String, Integer> counts = new HashMap<>();
        roomUsers.forEach((room, users) -> counts.put(room, users.size()));
        return counts;
    }
}