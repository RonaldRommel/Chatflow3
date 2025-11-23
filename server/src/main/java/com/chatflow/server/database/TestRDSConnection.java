package com.chatflow.server.database;
import java.sql.*;

public class TestRDSConnection {
    public static void main(String[] args) {
        String endpoint = "chatflow.c3cug4se63m4.us-west-2.rds.amazonaws.com";
        String url = "jdbc:postgresql://" + endpoint + ":5432/chatflow";
        String user = "chatflow";
        String password = "chatflow";

        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);
            System.out.println("✓ Connected successfully!");

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT 1");
            if (rs.next()) {
                System.out.println("✓ Query executed successfully!");
            }

            conn.close();
        } catch (Exception e) {
            System.err.println("✗ Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}