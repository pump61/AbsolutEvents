package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.command.CommandSender;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class UpdateChecker {

    private static final String OWNER = "pump61";
    private static final String REPOSITORY = "AbsolutEvents";
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/" + OWNER + "/" + REPOSITORY + "/releases/latest";

    private static final long CACHE_DURATION_MILLIS = 5L * 60L * 1000L;

    private static volatile UpdateInfo cachedInfo;
    private static volatile long lastCheckMillis;

    private UpdateChecker() {
    }

    public static void verify() {
        checkNowAsync().thenAccept(info -> {
            if (info == null || !info.isValid()) {
                AbsolutEventsPlugin.getInstance().getLogger().warning(
                        "Não foi possível verificar atualizações no GitHub."
                );
                return;
            }

            if (info.isUpdateAvailable()) {
                AbsolutEventsPlugin.getInstance().getLogger().warning("Nova versão disponível!");
                AbsolutEventsPlugin.getInstance().getLogger().warning("Versão atual: " + info.getCurrentVersion());
                AbsolutEventsPlugin.getInstance().getLogger().warning("Última versão: " + info.getLatestVersion());
                AbsolutEventsPlugin.getInstance().getLogger().warning("Release: " + info.getReleaseUrl());
                AbsolutEventsPlugin.getInstance().getLogger().warning("Use /evento update para ver detalhes.");
            } else {
                AbsolutEventsPlugin.getInstance().getLogger().info(
                        "Você já está usando a versão mais recente do plugin."
                );
            }
        }).exceptionally(throwable -> {
            AbsolutEventsPlugin.getInstance().getLogger().log(
                    Level.WARNING,
                    "Falha ao verificar atualização no GitHub.",
                    throwable
            );
            return null;
        });
    }

    public static CompletableFuture<UpdateInfo> getUpdateInfoAsync(boolean forceRefresh) {
        if (!forceRefresh && hasFreshCache()) {
            return CompletableFuture.completedFuture(cachedInfo);
        }

        return checkNowAsync();
    }

    public static UpdateInfo getCachedInfo() {
        return cachedInfo;
    }

    public static void sendUpdateInfo(CommandSender sender, UpdateInfo info) {
        if (info == null || !info.isValid()) {
            sender.sendMessage("§c[AbsolutEvents] Não foi possível obter informações da última release.");
            return;
        }

        sender.sendMessage("§8§m----------------------------------------");
        sender.sendMessage("§b§lAbsolutEvents §7- Atualizações");
        sender.sendMessage("§fVersão atual: §b" + info.getCurrentVersion());
        sender.sendMessage("§fÚltima versão: §b" + info.getLatestVersion());

        if (info.isUpdateAvailable()) {
            sender.sendMessage("§aHá uma atualização disponível.");
            if (info.hasDownload()) {
                sender.sendMessage("§fUse §b/evento update confirm §fpara baixar.");
            } else {
                sender.sendMessage("§eA release existe, mas não foi encontrado asset .jar para download.");
            }
        } else {
            sender.sendMessage("§aVocê já está na versão mais recente.");
        }

        if (info.getReleaseUrl() != null && !info.getReleaseUrl().isBlank()) {
            sender.sendMessage("§fRelease: §7" + info.getReleaseUrl());
        }

        if (info.getAssetName() != null && !info.getAssetName().isBlank()) {
            sender.sendMessage("§fArquivo: §7" + info.getAssetName());
        }

        sender.sendMessage("§8§m----------------------------------------");
    }

    private static boolean hasFreshCache() {
        return cachedInfo != null && (System.currentTimeMillis() - lastCheckMillis) < CACHE_DURATION_MILLIS;
    }

    private static CompletableFuture<UpdateInfo> checkNowAsync() {
        return CompletableFuture.supplyAsync(UpdateChecker::requestLatestRelease);
    }

    private static UpdateInfo requestLatestRelease() {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) URI.create(LATEST_RELEASE_API).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "AbsolutEvents-Updater");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                AbsolutEventsPlugin.getInstance().getLogger().warning(
                        "GitHub API retornou código " + responseCode + " ao verificar updates."
                );
                return null;
            }

            try (InputStream inputStream = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

                JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                if (json == null) {
                    return null;
                }

                String currentVersion = sanitizeVersion(
                        AbsolutEventsPlugin.getInstance().getDescription().getVersion()
                );

                String tagName = getAsString(json, "tag_name");
                String latestVersion = sanitizeVersion(tagName);
                String htmlUrl = getAsString(json, "html_url");
                String body = getAsString(json, "body");

                String downloadUrl = null;
                String assetName = null;

                JsonArray assets = json.has("assets") && json.get("assets").isJsonArray()
                        ? json.getAsJsonArray("assets")
                        : null;

                if (assets != null) {
                    for (JsonElement element : assets) {
                        if (!element.isJsonObject()) {
                            continue;
                        }

                        JsonObject asset = element.getAsJsonObject();
                        String name = getAsString(asset, "name");
                        String url = getAsString(asset, "browser_download_url");

                        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                            assetName = name;
                            downloadUrl = url;
                            break;
                        }
                    }
                }

                boolean updateAvailable = compareVersions(currentVersion, latestVersion) < 0;

                UpdateInfo info = new UpdateInfo(
                        currentVersion,
                        latestVersion,
                        tagName,
                        htmlUrl,
                        downloadUrl,
                        assetName,
                        body,
                        updateAvailable
                );

                cachedInfo = info;
                lastCheckMillis = System.currentTimeMillis();
                return info;
            }
        } catch (Exception exception) {
            AbsolutEventsPlugin.getInstance().getLogger().log(
                    Level.WARNING,
                    "Erro ao consultar a latest release do GitHub.",
                    exception
            );
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String getAsString(JsonObject json, String memberName) {
        if (json == null || !json.has(memberName) || json.get(memberName).isJsonNull()) {
            return null;
        }
        return json.get(memberName).getAsString();
    }

    public static String sanitizeVersion(String version) {
        if (version == null) {
            return "0.0.0";
        }

        String sanitized = version.trim();

        if (sanitized.startsWith("v") || sanitized.startsWith("V")) {
            sanitized = sanitized.substring(1);
        }

        int dashIndex = sanitized.indexOf('-');
        if (dashIndex >= 0) {
            sanitized = sanitized.substring(0, dashIndex);
        }

        return sanitized.trim();
    }

    public static int compareVersions(String first, String second) {
        String[] firstParts = sanitizeVersion(first).split("\\.");
        String[] secondParts = sanitizeVersion(second).split("\\.");

        int max = Math.max(firstParts.length, secondParts.length);

        for (int i = 0; i < max; i++) {
            int left = i < firstParts.length ? parseVersionPart(firstParts[i]) : 0;
            int right = i < secondParts.length ? parseVersionPart(secondParts[i]) : 0;

            if (left < right) {
                return -1;
            }
            if (left > right) {
                return 1;
            }
        }

        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            String digitsOnly = part.replaceAll("[^0-9]", "");
            if (digitsOnly.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(digitsOnly);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}