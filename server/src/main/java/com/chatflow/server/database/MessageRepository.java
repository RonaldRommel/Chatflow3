package com.chatflow.server.database;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MessageRepository {

    private final DataSource dataSource;
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);

    private static final String INSERT_SQL =
            "INSERT INTO messages (message_id, room_id, user_id, username, " +
                    "message, message_type, timestamp, server_id, client_ip) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (message_id) DO NOTHING";

    public MessageRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Batch insert messages for high throughput
     */
    public int batchInsert(List<PersistentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        long startTime = Instant.now().toEpochMilli();
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dataSource.getConnection();
            ps = conn.prepareStatement(INSERT_SQL);

            for (PersistentMessage msg : messages) {
                ps.setObject(1, msg.getMessageId());
                ps.setString(2, msg.getRoomId());
                ps.setInt(3, msg.getUserId());
                ps.setString(4, msg.getUsername());
                ps.setString(5, msg.getMessage());
                ps.setString(6, msg.getMessageType());
                ps.setTimestamp(7, Timestamp.from(msg.getTimestamp()));
                ps.setString(8, msg.getServerId());
                ps.setString(9, msg.getClientIp());
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            conn.commit();

            int inserted = 0;
            for (int result : results) {
                if (result > 0 || result == Statement.SUCCESS_NO_INFO) {
                    inserted++;
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            successCount.addAndGet(inserted);
            totalLatency.addAndGet(elapsed);

            return inserted;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { }
            }

            failureCount.addAndGet(messages.size());
            System.err.println("❌ Batch insert failed: " + e.getMessage());
            return 0;

        } finally {
            if (ps != null) try { ps.close(); } catch (SQLException e) { }
            if (conn != null) try { conn.close(); } catch (SQLException e) { }
        }
    }

    public long getSuccessCount() { return successCount.get(); }
    public long getFailureCount() { return failureCount.get(); }
    public double getAverageLatency() {
        long count = successCount.get();
        return count > 0 ? (double) totalLatency.get() / count : 0;
    }

    public void printStats() {
        System.out.println("\n=== Database Statistics ===");
        System.out.println("Messages written: " + successCount.get());
        System.out.println("Failed writes: " + failureCount.get());
        System.out.println("Average batch latency: " + String.format("%.2f", getAverageLatency()) + "ms");
    }
}