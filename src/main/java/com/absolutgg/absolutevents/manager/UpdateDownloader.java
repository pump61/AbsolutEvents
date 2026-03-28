package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class UpdateDownloader {

    private UpdateDownloader() {
    }

    public static CompletableFuture<Boolean> downloadLatestRelease(CommandSender sender) {
        return UpdateChecker.getUpdateInfoAsync(false).thenCompose(info -> {
            if (info == null || !info.isValid()) {
                sendSync(sender, "§c[AbsolutEvents] Não foi possível obter informações da release.");
                return CompletableFuture.completedFuture(false);
            }

            if (!info.isUpdateAvailable()) {
                sendSync(sender, "§a[AbsolutEvents] Você já está usando a versão mais recente.");
                return CompletableFuture.completedFuture(false);
            }

            if (!info.hasDownload()) {
                sendSync(sender, "§c[AbsolutEvents] A latest release não possui um arquivo .jar anexado.");
                return CompletableFuture.completedFuture(false);
            }

            sendSync(sender, "§e[AbsolutEvents] Baixando atualização do GitHub...");

            return CompletableFuture.supplyAsync(() -> downloadJar(info, sender));
        });
    }

    private static boolean downloadJar(UpdateInfo info, CommandSender sender) {
        HttpURLConnection connection = null;

        try {
            Path updateFolder = AbsolutEventsPlugin.getInstance()
                    .getDataFolder()
                    .toPath()
                    .getParent()
                    .resolve("update");

            Files.createDirectories(updateFolder);

            String fileName = info.getAssetName() != null && !info.getAssetName().isBlank()
                    ? info.getAssetName()
                    : "AbsolutEvents-" + info.getLatestVersion() + ".jar";

            Path targetFile = updateFolder.resolve(fileName);
            Path tempFile = updateFolder.resolve(fileName + ".download");

            connection = (HttpURLConnection) URI.create(info.getDownloadUrl()).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("User-Agent", "AbsolutEvents-Updater");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                sendSync(sender, "§c[AbsolutEvents] Falha ao baixar update. Código HTTP: " + responseCode);
                return false;
            }

            try (InputStream inputStream = connection.getInputStream();
                 OutputStream outputStream = Files.newOutputStream(tempFile)) {
                inputStream.transferTo(outputStream);
            }

            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

            sendSync(sender, "§a[AbsolutEvents] Update baixado com sucesso!");
            sendSync(sender, "§f[AbsolutEvents] Arquivo salvo em: §7plugins/update/" + fileName);
            sendSync(sender, "§e[AbsolutEvents] Reinicie o servidor para aplicar a nova versão.");
            return true;
        } catch (Exception exception) {
            AbsolutEventsPlugin.getInstance().getLogger().log(
                    Level.WARNING,
                    "Erro ao baixar a atualização do GitHub.",
                    exception
            );
            sendSync(sender, "§c[AbsolutEvents] Ocorreu um erro ao baixar a atualização. Veja o console.");
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void sendSync(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(AbsolutEventsPlugin.getInstance(), () -> sender.sendMessage(message));
    }
}