package com.absolutgg.absolutevents.utils;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class QuitCache {

    private static File file;
    private static YamlConfiguration config;

    private QuitCache() {
    }

    public static void init(File dataFolder) {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta do plugin: " + dataFolder.getAbsolutePath());
        }

        file = new File(dataFolder, "quit-cache.yml");

        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    throw new IOException("Não foi possível criar o arquivo quit-cache.yml");
                }
            } catch (IOException exception) {
                throw new RuntimeException("Erro ao criar quit-cache.yml", exception);
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
    }

    public static void setQuit(UUID uuid) {
        ensureInit();
        reload();

        config.set(uuid.toString(), true);
        save();
    }

    public static boolean wasInEvent(UUID uuid) {
        ensureInit();
        reload();

        return config.getBoolean(uuid.toString(), false);
    }

    public static void remove(UUID uuid) {
        ensureInit();
        reload();

        config.set(uuid.toString(), null);
        save();
    }

    private static void save() {
        try {
            config.save(file);
        } catch (IOException exception) {
            throw new RuntimeException("Erro ao salvar quit-cache.yml", exception);
        }
    }

    private static void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    private static void ensureInit() {
        if (file == null || config == null) {
            throw new IllegalStateException("QuitCache não foi inicializado. Chame QuitCache.init(getDataFolder()) no onEnable().");
        }
    }
}