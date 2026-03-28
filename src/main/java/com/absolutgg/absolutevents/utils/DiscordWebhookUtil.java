package com.absolutgg.absolutevents.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DiscordWebhookUtil {

    private static final String WEBHOOK_URL = "COLOCA_TUA_WEBHOOK_AQUI";

    public static void sendWinner(Player player, String eventName, List<String> top) {
        try {
            URL url = new URL(WEBHOOK_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String headUrl = "https://mc-heads.net/avatar/" + player.getName();

            StringBuilder topText = new StringBuilder();
            if (top != null && !top.isEmpty()) {
                topText.append("\\n\\n🏆 **TOP 3:**\\n");
                for (String line : top) {
                    topText.append(line).append("\\n");
                }
            }

            String json = "{"
                    + "\"embeds\": [{"
                    + "\"title\": \"🏆 Fim de Evento!\","
                    + "\"description\": \"**" + player.getName() + "** venceu o evento **" + eventName + "**!\""
                    + "+ \"" + topText + "\","
                    + "\"color\": 65280,"
                    + "\"thumbnail\": {\"url\": \"" + headUrl + "\"}"
                    + "}]"
                    + "}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            conn.getInputStream().close();

        } catch (Exception e) {
            Bukkit.getLogger().warning("[AbsolutEvents] Erro ao enviar webhook: " + e.getMessage());
        }
    }
}