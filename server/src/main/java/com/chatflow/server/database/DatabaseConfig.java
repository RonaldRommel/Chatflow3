package com.chatflow.server.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Value("${database.endpoint}")
    private String endpoint;

    @Value("${database.name:chatflow}")
    private String dbName;

    @Value("${database.username}")
    private String username;

    @Value("${database.password}")
    private String password;

    @Value("${database.pool.max-size:20}")
    private int maxPoolSize;

    @Value("${database.pool.min-idle:10}")
    private int minIdle;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        String jdbcUrl = String.format("jdbc:postgresql://%s:5432/%s", endpoint, dbName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        // Connection pool settings
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        // Performance optimizations
        config.setAutoCommit(false);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        HikariDataSource dataSource = new HikariDataSource(config);
        System.out.println("✓ Database connection pool initialized: " + maxPoolSize + " connections");

        return dataSource;
    }
}