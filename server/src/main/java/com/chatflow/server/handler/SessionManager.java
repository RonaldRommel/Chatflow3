package com.chatflow.server.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {
    private ConcurrentHashMap<String, WebSocketSession> sessions;

    public SessionManager() {
        this.sessions = new ConcurrentHashMap<>();
    }

    public ConcurrentHashMap<String, WebSocketSession> getSessions() {
        return sessions;
    }

    public void setSessions(ConcurrentHashMap<String, WebSocketSession> sessions) {
        this.sessions = sessions;
    }

    public WebSocketSession getSession(String roomId) {
        return sessions.get(roomId);
    }

    public void addSession(String roomId, WebSocketSession session) {
        sessions.put(roomId, session);
    }
}
