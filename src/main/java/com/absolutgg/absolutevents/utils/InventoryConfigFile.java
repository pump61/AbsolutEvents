package com.absolutgg.absolutevents.utils;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class InventoryConfigFile {

    private InventoryConfigFile() {
    }

    public static void create(@NotNull String name) {
        File folder = getPlayerDataFolder();
        File file = getPlayerFile(name);

        createDirectory(folder);

        if (file.exists()) {
            return;
        }

        try {
            file.createNewFile();
        } catch (IOException exception) {
            handleInventoryError(exception);
        }
    }

    public static void create(@NotNull String name, @NotNull String eventIdentifier) {
        File rootFolder = getPlayerDataFolder();
        File backupRoot = new File(rootFolder, "backup");
        File backupFolder = getBackupFolder(eventIdentifier);
        File file = getBackupFile(name, eventIdentifier);

        createDirectory(rootFolder);
        createDirectory(backupRoot);
        createDirectory(backupFolder);

        if (file.exists()) {
            return;
        }

        try {
            file.createNewFile();
        } catch (IOException exception) {
            handleInventoryError(exception);
        }
    }

    @NotNull
    public static YamlConfiguration get(@NotNull String name) {
        File file = getPlayerFile(name);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("filename", name + ".yml");

        return config;
    }

    @NotNull
    public static YamlConfiguration get(@NotNull String name, @NotNull String eventIdentifier) {
        File file = getBackupFile(name, eventIdentifier);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("filename", name + ".yml");

        return config;
    }

    public static boolean exists(@NotNull String name) {
        return getPlayerFile(name).exists();
    }

    public static boolean exists(@NotNull String name, @NotNull String eventIdentifier) {
        return getBackupFile(name, eventIdentifier).exists();
    }

    public static void save(@NotNull YamlConfiguration config) throws IOException {
        String filename = requireFilename(config);
        File folder = getPlayerDataFolder();
        File file = new File(folder, filename);

        createDirectory(folder);

        config.set("filename", null);
        config.save(file);
        config.set("filename", filename);
    }

    public static void save(@NotNull YamlConfiguration config, @NotNull String eventIdentifier) throws IOException {
        String filename = requireFilename(config);
        File rootFolder = getPlayerDataFolder();
        File backupRoot = new File(rootFolder, "backup");
        File folder = getBackupFolder(eventIdentifier);
        File file = new File(folder, filename);

        createDirectory(rootFolder);
        createDirectory(backupRoot);
        createDirectory(folder);

        config.set("filename", null);
        config.save(file);
        config.set("filename", filename);
    }

    public static void delete(@NotNull YamlConfiguration config) {
        String filename = config.getString("filename");
        if (filename == null || filename.isBlank()) {
            return;
        }

        File file = new File(getPlayerDataFolder(), filename);
        deleteFile(file);
    }

    public static void delete(@NotNull YamlConfiguration config, @NotNull String eventIdentifier) {
        String filename = config.getString("filename");
        if (filename == null || filename.isBlank()) {
            return;
        }

        File file = new File(getBackupFolder(eventIdentifier), filename);
        deleteFile(file);
    }

    @NotNull
    public static List<File> getAllFiles() {
        File folder = getPlayerDataFolder();

        if (!folder.exists() || !folder.isDirectory()) {
            return Collections.emptyList();
        }

        try {
            return Files.walk(folder.toPath())
                    .filter(Files::isRegularFile)
                    .map(java.nio.file.Path::toFile)
                    .collect(Collectors.toList());

        } catch (IOException exception) {
            exception.printStackTrace();
            return Collections.emptyList();
        }
    }

    @NotNull
    public static File getPlayerFile(@NotNull String name) {
        return new File(getPlayerDataFolder(), name + ".yml");
    }

    @NotNull
    public static File getBackupFile(@NotNull String name, @NotNull String eventIdentifier) {
        return new File(getBackupFolder(eventIdentifier), name + ".yml");
    }

    @NotNull
    public static File getBackupFolder(@NotNull String eventIdentifier) {
        String safeIdentifier = sanitizePathPart(eventIdentifier);
        return new File(new File(getPlayerDataFolder(), "backup"), safeIdentifier);
    }

    @NotNull
    private static File getPlayerDataFolder() {
        return new File(AbsolutEventsPlugin.getInstance().getDataFolder(), "playerdata");
    }

    private static void createDirectory(@NotNull File directory) {
        if (directory.exists()) {
            return;
        }

        if (!directory.mkdirs() && !directory.exists()) {
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Não foi possível criar a pasta: " + directory.getAbsolutePath());
        }
    }

    @NotNull
    private static String requireFilename(@NotNull YamlConfiguration config) {
        String filename = config.getString("filename");

        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("O campo temporário 'filename' não está definido no config.");
        }

        return filename;
    }

    @NotNull
    private static String sanitizePathPart(@NotNull String value) {
        String sanitized = value
                .replace("..", "")
                .replace("/", "_")
                .replace("\\", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .trim();

        return sanitized.isBlank() ? "unknown_event" : sanitized;
    }

    private static void deleteFile(@NotNull File file) {
        if (!file.exists()) {
            return;
        }

        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException exception) {
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Não foi possível remover arquivo de snapshot: " + file.getAbsolutePath());
            exception.printStackTrace();
        }
    }

    private static void handleInventoryError(@NotNull IOException exception) {
        Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao salvar snapshot de inventário.");
        exception.printStackTrace();
    }
}