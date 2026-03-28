package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.Bukkit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateChecker {

    private static final String CURRENT_VERSION =
            AbsolutEventsPlugin.getInstance().getDescription().getVersion();

    private UpdateChecker() {
    }

    public static void verify() {
        Bukkit.getScheduler().runTaskAsynchronously(AbsolutEventsPlugin.getInstance(), () -> {
            try {
                JSONParser parser = new JSONParser();

                URL url = new URL("https://api.github.com/repos/absolutgg/AbsolutEvents/releases");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setRequestProperty("accept", "application/json");

                StringBuilder content;

                try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    content = new StringBuilder();

                    while ((line = input.readLine()) != null) {
                        content.append(line).append(System.lineSeparator());
                    }
                } finally {
                    connection.disconnect();
                }

                JSONArray json = (JSONArray) parser.parse(content.toString());

                for (Object object : json) {
                    if (!(object instanceof JSONObject release)) {
                        continue;
                    }

                    Object tagName = release.get("tag_name");
                    Object draft = release.get("draft");
                    Object prerelease = release.get("prerelease");

                    if (tagName != null && tagName.equals(CURRENT_VERSION)) {
                        break;
                    }

                    if (Boolean.TRUE.equals(draft) || Boolean.TRUE.equals(prerelease)) {
                        continue;
                    }

                    Bukkit.getConsoleSender().sendMessage(
                            "§e[AbsolutEvents] §aUma nova atualização está disponível! (" + release.get("name") + ")"
                    );
                    Bukkit.getConsoleSender().sendMessage(
                            "§e[AbsolutEvents] §aVocê pode baixar aqui: " + release.get("html_url")
                    );
                    return;
                }

                Bukkit.getConsoleSender().sendMessage(
                        "§e[AbsolutEvents] §aVocê está usando a última versão. (v" + CURRENT_VERSION + ")"
                );

            } catch (IOException | ParseException exception) {
                Bukkit.getConsoleSender().sendMessage(
                        "§e[AbsolutEvents] §cNão foi possível verificar atualizações."
                );
                exception.printStackTrace();
            }
        });
    }
}