package com.chatflow.server.database;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
@Order(1) // Ensure this runs early
public class SchemaManager {

    private final DataSource dataSource;

    @Value("${database.reset-on-startup:true}")
    private boolean resetOnStartup;

    @Value("${database.create-indexes:false}")
    private boolean createIndexes;

    public SchemaManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initializeSchema() {
        if (!resetOnStartup) {
            System.out.println("⏭️  Skipping schema reset (database.reset-on-startup=false)");
            return;
        }

        System.out.println("\n=== Database Schema Initialization ===");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Drop existing table
            dropTables(stmt);

            // 2. Create messages table
            createMessagesTable(stmt);

            // 3. Optionally create indexes
            if (createIndexes) {
                createIndexes(stmt);
            } else {
                System.out.println("⏭️  Skipping index creation (create after loading data)");
            }

            conn.commit();

            // 4. Verify
            verifySchema(stmt);

            System.out.println("✓ Database schema initialized successfully\n");

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize database schema: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void dropTables(Statement stmt) throws Exception {
        System.out.println("🗑️  Dropping existing tables...");
        stmt.execute("DROP TABLE IF EXISTS messages CASCADE");
        System.out.println("   ✓ Tables dropped");
    }

    private void createMessagesTable(Statement stmt) throws Exception {
        System.out.println("📋 Creating messages table...");

        String createTableSQL =
                "CREATE TABLE messages (" +
                        "    message_id UUID PRIMARY KEY," +
                        "    room_id VARCHAR(10) NOT NULL," +
                        "    user_id INTEGER NOT NULL," +
                        "    username VARCHAR(20) NOT NULL," +
                        "    message TEXT NOT NULL," +
                        "    message_type VARCHAR(10) NOT NULL," +
                        "    timestamp TIMESTAMP NOT NULL," +
                        "    server_id VARCHAR(50)," +
                        "    client_ip VARCHAR(45)," +
                        "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";

        stmt.execute(createTableSQL);
        System.out.println("   ✓ Messages table created");
    }

    private void createIndexes(Statement stmt) throws Exception {
        System.out.println("🔍 Creating indexes...");

        String[] indexes = {
                "CREATE INDEX idx_room_timestamp ON messages(room_id, timestamp DESC)",
                "CREATE INDEX idx_user_timestamp ON messages(user_id, timestamp DESC)",
                "CREATE INDEX idx_timestamp ON messages(timestamp)",
                "CREATE INDEX idx_message_type ON messages(message_type)"
        };

        for (String indexSQL : indexes) {
            stmt.execute(indexSQL);
        }

        System.out.println("   ✓ Indexes created");
    }

    private void verifySchema(Statement stmt) throws Exception {
        System.out.println("🔍 Verifying schema...");

        ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_name = 'messages'"
        );

        if (rs.next() && rs.getInt(1) == 1) {
            System.out.println("   ✓ Messages table exists");
        } else {
            throw new RuntimeException("Messages table was not created!");
        }

        rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_name = 'messages'"
        );

        if (rs.next()) {
            int columnCount = rs.getInt(1);
            System.out.println("   ✓ Messages table has " + columnCount + " columns");
        }
    }

    /**
     * Create indexes after data load (better performance)
     */
    public void createIndexesAfterLoad() {
        System.out.println("\n=== Creating Indexes After Data Load ===");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            createIndexes(stmt);
            conn.commit();

            System.out.println("✓ Indexes created successfully\n");

        } catch (Exception e) {
            System.err.println("❌ Failed to create indexes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}