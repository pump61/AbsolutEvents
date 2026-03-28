package com.absolutgg.absolutevents.discord;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.Bukkit;

import java.io.OutputStream;
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
        String thumbnail = getString("Discord.Embeds.Winner.ThumbnailUrl", "https://mc-heads.net/avatar/%winner%/128")
                .replace("%winner%", player);

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

    public static void sendTeamWinner(String team, String event, List<TopEntry> topEntries) {
        if (!isEnabled()) {
            return;
        }

        String title = getString("Discord.Embeds.Winner.Title", "Fim de Papo!");
        String description = team + " venceu o evento " + event + "!";
        int color = getInt("Discord.Embeds.Winner.Color", 16711880);
        String footer = getString("Discord.Embeds.Winner.Footer", "");
        String thumbnail = "";

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

        if (!thumbnail.isBlank()) {
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
                fieldJson.add("{\"name\":\"" + escape("Top " + (i + 1)) + "\",\"value\":\"" +
                        escape(entry.name() + " - " + entry.value()) + "\",\"inline\":true}");
            }

            json.append(String.join(",", fieldJson));
            json.append("]");
        }

        json.append("}]}");
        return json.toString();
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