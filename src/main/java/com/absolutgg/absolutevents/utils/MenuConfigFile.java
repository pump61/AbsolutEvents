package com.absolutgg.absolutevents.utils;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class MenuConfigFile {

    private MenuConfigFile() {
    }

    public static void create(@NotNull String name) {
        File configFile = new File(getMenusFolder(), name + ".yml");
        if (configFile.exists()) {
            return;
        }

        AbsolutEventsPlugin.getInstance().saveResource("menus/" + name + ".yml", false);
    }

    @NotNull
    public static YamlConfiguration get(@NotNull String name) {
        File file = new File(getMenusFolder(), name + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("filename", name + ".yml");
        return config;
    }

    public static boolean exists(@NotNull String name) {
        File file = new File(getMenusFolder(), name + ".yml");
        return file.exists();
    }

    public static void save(@NotNull YamlConfiguration config) throws IOException {
        String filename = config.getString("filename");
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("O campo temporário 'filename' não está definido no config.");
        }

        File file = new File(getMenusFolder(), filename);
        config.set("filename", null);
        config.save(file);
        config.set("filename", filename);
    }

    @NotNull
    public static List<File> getAllFiles() {
        File menusFolder = getMenusFolder();

        if (!menusFolder.exists()) {
            return Collections.emptyList();
        }

        try {
            return Files.walk(menusFolder.toPath())
                    .filter(Files::isRegularFile)
                    .map(java.nio.file.Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            AbsolutEventsPlugin.getInstance().getLogger().severe("Não foi possível listar os arquivos da pasta menus.");
            exception.printStackTrace();
            return Collections.emptyList();
        }
    }

    @NotNull
    private static File getMenusFolder() {
        return new File(AbsolutEventsPlugin.getInstance().getDataFolder(), "menus");
    }
}