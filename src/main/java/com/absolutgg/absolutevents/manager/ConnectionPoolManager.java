package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ConnectionPoolManager {

    private HikariDataSource dataSource;

    private String hostname;
    private int port;
    private String database;
    private String username;
    private String password;

    private int minimumConnections;
    private int maximumConnections;
    private long connectionTimeout;

    public ConnectionPoolManager() {
        init();
        setupPool();
    }

    private void init() {
        hostname = AbsolutEventsPlugin.getInstance().getConfig().getString("MySQL.Host", "localhost");
        port = AbsolutEventsPlugin.getInstance().getConfig().getInt("MySQL.Port", 3306);
        database = AbsolutEventsPlugin.getInstance().getConfig().getString("MySQL.Database", "absolutevents");
        username = AbsolutEventsPlugin.getInstance().getConfig().getString("MySQL.Username", "root");
        password = AbsolutEventsPlugin.getInstance().getConfig().getString("MySQL.Password", "");

        minimumConnections = 3;
        maximumConnections = 15;
        connectionTimeout = 10000L;
    }

    private void setupPool() {
        if (!AbsolutEventsPlugin.getInstance().getConfig().getBoolean("MySQL.Enabled")) {
            return;
        }

        HikariConfig config = new HikariConfig();

        config.setPoolName("AbsolutEvents-Hikari");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(
                "jdbc:mysql://" + hostname + ":" + port + "/" + database
                        + "?useSSL=false"
                        + "&allowPublicKeyRetrieval=true"
                        + "&characterEncoding=utf8"
                        + "&useUnicode=true"
                        + "&serverTimezone=UTC"
        );

        config.setUsername(username);
        config.setPassword(password);

        config.setMinimumIdle(minimumConnections);
        config.setMaximumPoolSize(maximumConnections);
        config.setConnectionTimeout(connectionTimeout);
        config.setValidationTimeout(5000L);
        config.setLeakDetectionThreshold(15000L);
        config.setAutoCommit(true);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("O pool de conexões MySQL não está disponível.");
        }

        return dataSource.getConnection();
    }

    public boolean isActive() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void close(
            boolean isSqlite,
            @Nullable Connection connection,
            @Nullable PreparedStatement statement,
            @Nullable ResultSet resultSet
    ) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
        }

        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ignored) {
            }
        }

        if (!isSqlite && connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}