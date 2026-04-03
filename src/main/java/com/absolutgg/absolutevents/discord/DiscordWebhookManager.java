package com.absolutgg.absolutevents.discord;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DiscordWebhookManager {

    private DiscordWebhookManager() {
    }

    public static void sendPlayerWinner(String player, String event) {
        sendPlayerWinner(player, event, List.of());
    }

    public static void sendPlayerWinner(String player, String event, List<TopEntry> topEntries) {
        if (!isEnabled()) {
            return;
        }

        String title = getString("Discord.Embeds.Winner.Title", "Fim de Papo!");
        String description = getString("Discord.Embeds.Winner.Description", "%winner% venceu o evento %event%!")
                .replace("%winner%", player)
                .replace("%event%", event);

        int color = getInt("Discord.Embeds.Winner.Color", 16711880);
        String footer = getString("Discord.Embeds.Winner.Footer", "");
        String configuredThumbnail = getString("Discord.Embeds.Winner.ThumbnailUrl", "");
        String thumbnail = resolveConfiguredOrPlayerThumbnail(configuredThumbnail, player);

        sendEmbed(title, description, color, thumbnail, footer, topEntries);
    }

    public static void sendMultipleWinners(List<String> winners, String event) {
        sendMultipleWinners(winners, event, List.of());
    }

    public static void sendMultipleWinners(List<String> winners, String event, List<TopEntry> topEntries) {
        if (!isEnabled()) {
            return;
        }

        if (winners == null || winners.isEmpty()) {
            return;
        }

        if (winners.size() == 1) {
            sendPlayerWinner(winners.get(0), event, topEntries);
            return;
        }

        String title = getString("Discord.Embeds.MultipleWinners.Title", "Fim de Papo!");
        String description = getString("Discord.Embeds.MultipleWinners.Description", "Os vencedores do evento %event% foram:%winners%")
                .replace("%event%", event)
                .replace("%winners%", formatWinnersList(winners));

        int color = getInt("Discord.Embeds.MultipleWinners.Color", 16711880);
        String footer = getString("Discord.Embeds.MultipleWinners.Footer", "");
        String configuredThumbnail = getString("Discord.Embeds.MultipleWinners.ThumbnailUrl", "");
        String thumbnail = resolveConfiguredOrPlayerThumbnail(configuredThumbnail, winners.get(0));

        sendEmbed(title, description, color, thumbnail, footer, topEntries);
    }

    public static void sendTeamWinner(String team, String event) {
        sendTeamWinner(team, event, List.of());
    }

    public static void sendTeamWinner(String team, String event, List<TopEntry> topEntries) {
        if (!isEnabled()) {
            return;
        }

        String title = getString("Discord.Embeds.TeamWinner.Title", "Fim de Papo!");
        String description = getString("Discord.Embeds.TeamWinner.Description", "O time %team% venceu o evento %event%!")
                .replace("%team%", team)
                .replace("%event%", event);

        int color = getInt("Discord.Embeds.TeamWinner.Color", 16711880);
        String footer = getString("Discord.Embeds.TeamWinner.Footer", "");
        String thumbnail = getString("Discord.Embeds.TeamWinner.ThumbnailUrl", "");

        sendEmbed(title, description, color, thumbnail, footer, topEntries);
    }

    public static void sendTeamWinnerWithPlayers(String team, String event, List<String> players) {
        sendTeamWinnerWithPlayers(team, event, players, List.of());
    }

    public static void sendTeamWinnerWithPlayers(String team, String event, List<String> players, List<TopEntry> topEntries) {
        if (!isEnabled()) {
            return;
        }

        String title = getString("Discord.Embeds.TeamWinner.Title", "Fim de Papo!");

        String baseDescription = getString(
                "Discord.Embeds.TeamWinner.Description",
                "O time %team% venceu o evento %event%!"
        )
                .replace("%team%", team)
                .replace("%event%", event);

        String playersText = "";
        if (players != null && !players.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (String name : players) {
                builder.append("\n• ").append(name);
            }
            playersText = "\n\n👥 Jogadores:" + builder;
        }

        String description = baseDescription + playersText;

        int color = getInt("Discord.Embeds.TeamWinner.Color", 16711880);
        String footer = getString("Discord.Embeds.TeamWinner.Footer", "");
        String configuredThumbnail = getString("Discord.Embeds.TeamWinner.ThumbnailUrl", "");
        String thumbnail = (players != null && !players.isEmpty())
                ? resolveConfiguredOrPlayerThumbnail(configuredThumbnail, players.get(0))
                : configuredThumbnail;

        sendEmbed(title, description, color, thumbnail, footer, topEntries);
    }

    public static void sendClanWinner(String clan, String event) {
        sendClanWinner(clan, event, List.of());
    }

    public static void sendClanWinner(String clan, String event, List<TopEntry> topEntries) {
        if (!isEnabled()) {
            return;
        }

        String title = getString("Discord.Embeds.ClanWinner.Title", "Fim de Papo!");
        String description = getString("Discord.Embeds.ClanWinner.Description", "O clã %clan% venceu o evento %event%!")
                .replace("%clan%", clan)
                .replace("%event%", event);

        int color = getInt("Discord.Embeds.ClanWinner.Color", 9109504);
        String footer = getString("Discord.Embeds.ClanWinner.Footer", "Evento exclusivo do servidor.");
        String thumbnail = getString("Discord.Embeds.ClanWinner.ThumbnailUrl", "");

        sendEmbed(title, description, color, thumbnail, footer, topEntries);
    }

    private static void sendEmbed(String title, String description, int color, String thumbnail, String footer, List<TopEntry> topEntries) {
        Bukkit.getScheduler().runTaskAsynchronously(AbsolutEventsPlugin.getInstance(), () -> {
            try {
                String webhookUrl = getString("Discord.Webhook.Url", "");
                if (webhookUrl.isBlank()) {
                    return;
                }

                HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setDoOutput(true);

                String payload = buildPayload(title, description, color, thumbnail, footer, topEntries);

                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    AbsolutEventsPlugin.getInstance().getLogger().warning("Webhook do Discord respondeu com código " + responseCode + ".");
                }

                connection.disconnect();
            } catch (Exception exception) {
                AbsolutEventsPlugin.getInstance().getLogger().warning("Erro ao enviar webhook do Discord.");
                exception.printStackTrace();
            }
        });
    }

    private static String buildPayload(String title, String description, int color, String thumbnail, String footer, List<TopEntry> topEntries) {
        String username = getString("Discord.Webhook.Username", "AbsolutEvents");
        String avatarUrl = getString("Discord.Webhook.AvatarUrl", "");

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"username\":\"").append(escape(username)).append("\"");

        if (!avatarUrl.isBlank()) {
            json.append(",\"avatar_url\":\"").append(escape(avatarUrl)).append("\"");
        }

        json.append(",\"embeds\":[{");
        json.append("\"title\":\"").append(escape(title)).append("\",");
        json.append("\"description\":\"").append(escape(description)).append("\",");
        json.append("\"color\":").append(color);

        if (thumbnail != null && !thumbnail.isBlank()) {
            json.append(",\"thumbnail\":{\"url\":\"").append(escape(thumbnail)).append("\"}");
        }

        if (!footer.isBlank()) {
            json.append(",\"footer\":{\"text\":\"").append(escape(footer)).append("\"}");
        }

        List<TopEntry> safeTopEntries = topEntries == null ? List.of() : topEntries;
        if (!safeTopEntries.isEmpty()) {
            json.append(",\"fields\":[");
            List<String> fieldJson = new ArrayList<>();

            for (int i = 0; i < safeTopEntries.size(); i++) {
                TopEntry entry = safeTopEntries.get(i);
                fieldJson.add(
                        "{\"name\":\"" + escape("Top " + (i + 1)) + "\",\"value\":\""
                                + escape(entry.name() + " - " + entry.value()) + "\",\"inline\":true}"
                );
            }

            json.append(String.join(",", fieldJson));
            json.append("]");
        }

        json.append("}]}");
        return json.toString();
    }

    private static String formatWinnersList(List<String> winners) {
        StringBuilder builder = new StringBuilder();

        for (String winner : winners) {
            builder.append("\n• ").append(winner);
        }

        return builder.toString();
    }

    private static String resolvePlayerThumbnail(String playerName) {
        String fallback = "https://mc-heads.net/avatar/" + playerName + "/128";

        try {
            Plugin srPlugin = Bukkit.getPluginManager().getPlugin("SkinsRestorer");

            if (srPlugin != null && srPlugin.isEnabled()) {
                Player player = Bukkit.getPlayerExact(playerName);

                if (player != null) {
                    Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
                    Method getMethod = providerClass.getMethod("get");
                    Object skinsRestorer = getMethod.invoke(null);

                    Method getPlayerStorageMethod = skinsRestorer.getClass().getMethod("getPlayerStorage");
                    Object playerStorage = getPlayerStorageMethod.invoke(skinsRestorer);

                    Method getSkinForPlayerMethod = playerStorage.getClass().getMethod(
                            "getSkinForPlayer",
                            java.util.UUID.class,
                            String.class
                    );
                    Object optionalProperty = getSkinForPlayerMethod.invoke(
                            playerStorage,
                            player.getUniqueId(),
                            player.getName()
                    );

                    if (optionalProperty instanceof java.util.Optional<?> optional && optional.isPresent()) {
                        Object skinProperty = optional.get();

                        Class<?> propertyUtilsClass = Class.forName("net.skinsrestorer.api.PropertyUtils");
                        Method getSkinTextureUrlMethod = propertyUtilsClass.getMethod(
                                "getSkinTextureUrl",
                                Class.forName("net.skinsrestorer.api.property.SkinProperty")
                        );

                        Object textureUrl = getSkinTextureUrlMethod.invoke(null, skinProperty);

                        if (textureUrl instanceof String url && !url.isBlank()) {
                            String textureHash = extractTextureHash(url);
                            if (textureHash != null && !textureHash.isBlank()) {
                                return "https://mc-heads.net/avatar/" + textureHash + "/128";
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return fallback;
    }

    private static String extractTextureHash(String textureUrl) {
        if (textureUrl == null || textureUrl.isBlank()) {
            return null;
        }

        String cleaned = textureUrl.trim();

        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash >= cleaned.length() - 1) {
            return null;
        }

        String hash = cleaned.substring(lastSlash + 1).trim();

        int queryIndex = hash.indexOf('?');
        if (queryIndex != -1) {
            hash = hash.substring(0, queryIndex);
        }

        return hash.isBlank() ? null : hash;
    }

    private static String resolveConfiguredOrPlayerThumbnail(String configuredThumbnail, String playerName) {
        if (configuredThumbnail != null && !configuredThumbnail.isBlank()) {
            return configuredThumbnail.replace("%winner%", playerName);
        }

        return resolvePlayerThumbnail(playerName);
    }

    private static boolean isEnabled() {
        return AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Discord.Enabled", false);
    }

    private static String getString(String path, String fallback) {
        return AbsolutEventsPlugin.getInstance().getConfig().getString(path, fallback);
    }

    private static int getInt(String path, int fallback) {
        return AbsolutEventsPlugin.getInstance().getConfig().getInt(path, fallback);
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }

    public record TopEntry(String name, String value) {
    }
}