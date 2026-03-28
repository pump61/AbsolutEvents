package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("deprecation")
public final class ConversorConnectionManager {

    private Connection connection;

    private void openConnection() throws SQLException, ClassNotFoundException {
        ConfigurationSection section = AbsolutEventsPlugin.getInstance()
                .getConfig()
                .getConfigurationSection("Conversor.MySQL");

        if (section == null) {
            throw new IllegalStateException("Seção Conversor.MySQL não encontrada.");
        }

        boolean mysql = section.getBoolean("Enabled");

        if (connection != null && !connection.isClosed()) {
            return;
        }

        if (mysql) {
            String host = section.getString("Host");
            int port = section.getInt("Port");
            String username = section.getString("Username");
            String password = section.getString("Password");
            String database = section.getString("Database");

            Class.forName("com.mysql.cj.jdbc.Driver");

            connection = DriverManager.getConnection(
                    "jdbc:mysql://" + host + ":" + port + "/" + database,
                    username,
                    password
            );
        } else {
            Class.forName("org.sqlite.JDBC");

            File dbFile = new File(
                    AbsolutEventsPlugin.getInstance().getDataFolder(),
                    "convert.db"
            );

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        }
    }

    public void setup() {
        try {
            if (connection == null || connection.isClosed()) {
                openConnection();
            }
        } catch (Exception exception) {
            Bukkit.getConsoleSender().sendMessage(
                    "§c[AbsolutEvents] Não foi possível conectar ao banco do plugin antigo."
            );
            exception.printStackTrace();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            Bukkit.getConsoleSender().sendMessage(
                    "§c[AbsolutEvents] Não foi possível encerrar conexão do conversor."
            );
            exception.printStackTrace();
        }
    }

    public boolean convertHEventos() {
        try (
                PreparedStatement statement = connection.prepareStatement("SELECT player,wins,participations FROM eventos");
                ResultSet results = statement.executeQuery()
        ) {
            while (results.next()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(results.getString("player"));

                AbsolutEventsPlugin.getInstance().getConnectionManager().insertUser(player.getUniqueId());
                AbsolutEventsPlugin.getInstance().getConnectionManager().setTotalWins(player.getUniqueId(), results.getInt("wins"));
                AbsolutEventsPlugin.getInstance().getConnectionManager().setTotalParticipations(player.getUniqueId(), results.getInt("participations"));
            }

            return true;

        } catch (SQLException exception) {
            Bukkit.getConsoleSender().sendMessage(
                    "§c[AbsolutEvents] Erro ao converter dados do HEventos."
            );
            exception.printStackTrace();
        }

        return false;
    }

    public boolean convertYEventos() {
        try (
                PreparedStatement statement = connection.prepareStatement("SELECT key,json FROM evento");
                ResultSet results = statement.executeQuery()
        ) {
            while (results.next()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(results.getString("key"));

                JsonObject jsonObject = JsonParser.parseString(results.getString("json")).getAsJsonObject();

                AbsolutEventsPlugin.getInstance().getConnectionManager().insertUser(player.getUniqueId());

                Set<Map.Entry<String, JsonElement>> entries = jsonObject.entrySet();

                for (Map.Entry<String, JsonElement> entry : entries) {
                    String eventName = AbsolutEventsPlugin.getInstance()
                            .getConfig()
                            .getString("Conversor.yEventos." + entry.getKey());

                    if (eventName == null) {
                        continue;
                    }

                    int wins = entry.getValue().getAsInt();

                    AbsolutEventsPlugin.getInstance().getConnectionManager().addWins(eventName, player.getUniqueId(), wins);
                    AbsolutEventsPlugin.getInstance().getConnectionManager().addParticipations(eventName, player.getUniqueId(), wins);
                }
            }

            return true;

        } catch (SQLException exception) {
            Bukkit.getConsoleSender().sendMessage(
                    "§c[AbsolutEvents] Erro ao converter dados do yEventos."
            );
            exception.printStackTrace();
        }

        return false;
    }
}