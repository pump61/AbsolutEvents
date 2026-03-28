package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ConnectionManager {

    private final ConnectionPoolManager pool;
    private Connection sqliteConnection;

    private final boolean sqlite;
    private final Gson gson = new Gson();

    public ConnectionManager() {
        this.pool = new ConnectionPoolManager();
        this.sqlite = !AbsolutEventsPlugin.getInstance().getConfig().getBoolean("MySQL.Enabled");
    }

    public boolean setup() {
        try {
            Connection conn = getConnection();

            try (Statement statement = conn.createStatement()) {
                if (sqlite) {
                    statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS absolutevents_users(
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            username TEXT NOT NULL,
                            uuid TEXT NOT NULL UNIQUE,
                            total_wins INT NOT NULL,
                            total_participations INT NOT NULL,
                            wins TEXT NOT NULL,
                            participations TEXT NOT NULL)
                            """);
                } else {
                    statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS absolutevents_users(
                            id INT AUTO_INCREMENT PRIMARY KEY,
                            username TEXT NOT NULL,
                            uuid TEXT NOT NULL UNIQUE,
                            total_wins INT NOT NULL,
                            total_participations INT NOT NULL,
                            wins LONGTEXT NOT NULL,
                            participations LONGTEXT NOT NULL)
                            """);
                }
            }

            return true;

        } catch (SQLException exception) {
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao criar tabelas.");
            exception.printStackTrace();
            return false;
        }
    }

    public void close() {
        try {
            if (sqlite && sqliteConnection != null && !sqliteConnection.isClosed()) {
                sqliteConnection.close();
            }

            pool.closePool();
        } catch (SQLException ignored) {
        }
    }

    private Connection getConnection() throws SQLException {
        if (sqlite) {
            return getSQLiteConnection();
        }

        return pool.getConnection();
    }

    private Connection getSQLiteConnection() {
        try {
            if (sqliteConnection == null || sqliteConnection.isClosed()) {
                Class.forName("org.sqlite.JDBC");

                File file = new File(
                        AbsolutEventsPlugin.getInstance().getDataFolder(),
                        "storage.db"
                );

                sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + file);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        return sqliteConnection;
    }

    private void closeIfNeeded(Connection connection) {
        if (!sqlite && connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public boolean isEmpty() {
        Connection conn = null;

        try {
            conn = getConnection();

            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS total FROM absolutevents_users");
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt("total") == 0;
                }
            }

        } catch (SQLException exception) {
            exception.printStackTrace();
        } finally {
            closeIfNeeded(conn);
        }

        return true;
    }

    public void insertUser(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            String sql = sqlite
                    ? "INSERT OR IGNORE INTO absolutevents_users VALUES(NULL,?,?,?,?,?,?)"
                    : "INSERT IGNORE INTO absolutevents_users VALUES(NULL,?,?,?,?,?,?)";

            Connection conn = null;

            try {
                conn = getConnection();

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

                    ps.setString(1, player.getName() == null ? "Unknown" : player.getName());
                    ps.setString(2, uuid.toString());
                    ps.setInt(3, 0);
                    ps.setInt(4, 0);
                    ps.setString(5, "{}");
                    ps.setString(6, "{}");

                    ps.executeUpdate();
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            } finally {
                closeIfNeeded(conn);
            }
        });
    }

    public void addWin(String event, UUID uuid) {
        CompletableFuture.runAsync(() -> {
            Connection conn = null;

            try {
                conn = getConnection();

                JsonObject wins = getWins(uuid);
                int value = wins.has(event) ? wins.get(event).getAsInt() : 0;
                wins.addProperty(event, value + 1);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE absolutevents_users SET wins=?, total_wins=total_wins+1 WHERE uuid=?"
                )) {
                    ps.setString(1, gson.toJson(wins));
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            } finally {
                closeIfNeeded(conn);
            }
        });
    }

    public void addWins(String event, UUID uuid, int amount) {
        if (amount <= 0) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            Connection conn = null;

            try {
                conn = getConnection();

                JsonObject wins = getWins(uuid);
                int value = wins.has(event) ? wins.get(event).getAsInt() : 0;
                wins.addProperty(event, value + amount);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE absolutevents_users SET wins=?, total_wins=total_wins+? WHERE uuid=?"
                )) {
                    ps.setString(1, gson.toJson(wins));
                    ps.setInt(2, amount);
                    ps.setString(3, uuid.toString());
                    ps.executeUpdate();
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            } finally {
                closeIfNeeded(conn);
            }
        });
    }

    public JsonObject getWins(UUID uuid) {
        Connection conn = null;

        try {
            conn = getConnection();

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT wins FROM absolutevents_users WHERE uuid=?"
            )) {
                ps.setString(1, uuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String value = rs.getString("wins");
                        return value == null || value.isBlank()
                                ? new JsonObject()
                                : gson.fromJson(value, JsonObject.class);
                    }
                }
            }

        } catch (SQLException exception) {
            exception.printStackTrace();
        } finally {
            closeIfNeeded(conn);
        }

        return new JsonObject();
    }

    public void addParticipation(String event, UUID uuid) {
        CompletableFuture.runAsync(() -> {
            Connection conn = null;

            try {
                conn = getConnection();

                JsonObject participations = getParticipations(uuid);
                int value = participations.has(event) ? participations.get(event).getAsInt() : 0;
                participations.addProperty(event, value + 1);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE absolutevents_users SET participations=?, total_participations=total_participations+1 WHERE uuid=?"
                )) {
                    ps.setString(1, gson.toJson(participations));
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            } finally {
                closeIfNeeded(conn);
            }
        });
    }

    public void addParticipations(String event, UUID uuid, int amount) {
        if (amount <= 0) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            Connection conn = null;

            try {
                conn = getConnection();

                JsonObject participations = getParticipations(uuid);
                int value = participations.has(event) ? participations.get(event).getAsInt() : 0;
                participations.addProperty(event, value + amount);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE absolutevents_users SET participations=?, total_participations=total_participations+? WHERE uuid=?"
                )) {
                    ps.setString(1, gson.toJson(participations));
                    ps.setInt(2, amount);
                    ps.setString(3, uuid.toString());
                    ps.executeUpdate();
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            } finally {
                closeIfNeeded(conn);
            }
        });
    }

    public JsonObject getParticipations(UUID uuid) {
        Connection conn = null;

        try {
            conn = getConnection();

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT participations FROM absolutevents_users WHERE uuid=?"
            )) {
                ps.setString(1, uuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String value = rs.getString("participations");
                        return value == null || value.isBlank()
                                ? new JsonObject()
                                : gson.fromJson(value, JsonObject.class);
                    }
                }
            }

        } catch (SQLException exception) {
            exception.printStackTrace();
        } finally {
            closeIfNeeded(conn);
        }

        return new JsonObject();
    }

    public void setTotalWins(UUID uuid, int totalWins) {
        CompletableFuture.runAsync(() -> {
            Connection conn = null;

            try {
                conn = getConnection();

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE absolutevents_users SET total_wins=? WHERE uuid=?"
                )) {
                    ps.setInt(1, Math.max(totalWins, 0));
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            } finally {
                closeIfNeeded(conn);
            }
        });
    }

    public void setTotalParticipations(UUID uuid, int totalParticipations) {
        CompletableFuture.runAsync(() -> {
            Connection conn = null;

            try {
                conn = getConnection();

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE absolutevents_users SET total_participations=? WHERE uuid=?"
                )) {
                    ps.setInt(1, Math.max(totalParticipations, 0));
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            } finally {
                closeIfNeeded(conn);
            }
        });
    }

    public void setEventoWinner(String event, List<String> winners) {
        if (winners == null || winners.isEmpty()) {
            return;
        }

        for (String uuidString : winners) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                insertUser(uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void setEventoGuildWinner(String event, String guildName, HashMap<org.bukkit.OfflinePlayer, Integer> kills, List<String> winners) {
        setEventoWinner(event, winners);
    }

    public void getPlayersWins() {
        Connection conn = null;

        try {
            conn = getConnection();

            try (PreparedStatement ps = conn.prepareStatement("SELECT uuid, wins FROM absolutevents_users");
                 ResultSet rs = ps.executeQuery()) {

                AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerWinsList().clear();

                while (rs.next()) {
                    String uuidString = rs.getString("uuid");
                    String winsString = rs.getString("wins");

                    if (uuidString == null || uuidString.isBlank()) {
                        continue;
                    }

                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidString);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }

                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    JsonObject winsJson = (winsString == null || winsString.isBlank())
                            ? new JsonObject()
                            : gson.fromJson(winsString, JsonObject.class);

                    Map<String, Integer> parsedWins = new HashMap<>();
                    for (String key : winsJson.keySet()) {
                        try {
                            parsedWins.put(key, winsJson.get(key).getAsInt());
                        } catch (Exception ignored) {
                        }
                    }

                    AbsolutEventsPlugin.getInstance().getCacheManager()
                            .getPlayerWinsList()
                            .put(player, parsedWins);
                }
            }

            AbsolutEventsPlugin.getInstance().getCacheManager().calculateTopWins();

        } catch (SQLException exception) {
            exception.printStackTrace();
        } finally {
            closeIfNeeded(conn);
        }
    }

    public void getPlayersParticipations() {
        Connection conn = null;

        try {
            conn = getConnection();

            try (PreparedStatement ps = conn.prepareStatement("SELECT uuid, participations FROM absolutevents_users");
                 ResultSet rs = ps.executeQuery()) {

                AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerParticipationsList().clear();

                while (rs.next()) {
                    String uuidString = rs.getString("uuid");
                    String participationsString = rs.getString("participations");

                    if (uuidString == null || uuidString.isBlank()) {
                        continue;
                    }

                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidString);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }

                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    JsonObject participationsJson = (participationsString == null || participationsString.isBlank())
                            ? new JsonObject()
                            : gson.fromJson(participationsString, JsonObject.class);

                    Map<String, Integer> parsedParticipations = new HashMap<>();
                    for (String key : participationsJson.keySet()) {
                        try {
                            parsedParticipations.put(key, participationsJson.get(key).getAsInt());
                        } catch (Exception ignored) {
                        }
                    }

                    AbsolutEventsPlugin.getInstance().getCacheManager()
                            .getPlayerParticipationsList()
                            .put(player, parsedParticipations);
                }
            }

            AbsolutEventsPlugin.getInstance().getCacheManager().calculateTopParticipations();

        } catch (SQLException exception) {
            exception.printStackTrace();
        } finally {
            closeIfNeeded(conn);
        }
    }
}