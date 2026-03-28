package com.absolutgg.absolutevents.utils;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoType;
import com.absolutgg.absolutevents.utils.converters.config.SerializerConverter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    public static void update(Plugin plugin, String resourceName, File toUpdate, List<String> ignoredSections) throws IOException {
        if (plugin.getResource(resourceName) == null) {
            return;
        }

        BufferedReader newReader = new BufferedReader(
                new InputStreamReader(plugin.getResource(resourceName), StandardCharsets.UTF_8)
        );

        List<String> newLines = newReader.lines().collect(Collectors.toList());
        newReader.close();

        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(toUpdate);
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(Objects.requireNonNull(plugin.getResource(resourceName)), StandardCharsets.UTF_8)
        );

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(toUpdate), StandardCharsets.UTF_8)
        );

        List<String> ignoredSectionsArrayList = new ArrayList<>(ignoredSections);
        ignoredSectionsArrayList.removeIf(ignoredSection -> !newConfig.isConfigurationSection(ignoredSection));

        Yaml yaml = new Yaml();
        Map<String, String> comments = parseComments(newLines, ignoredSectionsArrayList, oldConfig, yaml);

        write(newConfig, oldConfig, comments, ignoredSectionsArrayList, writer, yaml);
    }

    private static Map<String, String> parseComments(List<String> newLines, List<String> ignoredSections, FileConfiguration oldConfig, Yaml yaml) {
        Map<String, String> comments = new LinkedHashMap<>();

        StringBuilder currentComment = new StringBuilder();
        List<String> path = new ArrayList<>();

        for (String line : newLines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                currentComment.append(line).append("\n");
                continue;
            }

            if (trimmed.startsWith("- ")) {
                continue;
            }

            int indents = countIndents(line);
            while (path.size() > indents) {
                path.remove(path.size() - 1);
            }

            String key = trimmed;
            int colonIndex = key.indexOf(':');
            if (colonIndex == -1) {
                continue;
            }

            key = key.substring(0, colonIndex).trim();
            path.add(key);

            String fullKey = String.join(".", path);

            if (currentComment.length() > 0) {
                comments.put(fullKey, currentComment.toString());
                currentComment.setLength(0);
            }
        }

        if (currentComment.length() > 0) {
            comments.put(null, currentComment.toString());
        }

        return comments;
    }

    private static void write(FileConfiguration newConfig, FileConfiguration oldConfig, Map<String, String> comments,
                              List<String> ignoredSections, BufferedWriter writer, Yaml yaml) throws IOException {

        outer:
        for (String key : newConfig.getKeys(true)) {
            String[] keys = key.split("\\.");
            String actualKey = keys[keys.length - 1];

            String comment = comments.remove(key);

            StringBuilder prefixBuilder = new StringBuilder();
            int indents = keys.length - 1;

            appendPrefixSpaces(prefixBuilder, indents);

            String prefixSpaces = prefixBuilder.toString();

            if (comment != null) {
                writer.write(comment);
            }

            for (String ignoredSection : ignoredSections) {
                if (key.startsWith(ignoredSection)) {
                    continue outer;
                }
            }

            Object newObj = newConfig.get(key);
            Object oldObj = oldConfig.get(key);

            if (newObj instanceof ConfigurationSection && oldObj instanceof ConfigurationSection) {
                writeSection(writer, actualKey, prefixSpaces, (ConfigurationSection) oldObj);
            } else if (newObj instanceof ConfigurationSection) {
                writeSection(writer, actualKey, prefixSpaces, (ConfigurationSection) newObj);
            } else if (oldObj != null) {
                write(oldObj, actualKey, prefixSpaces, yaml, writer);
            } else {
                write(newObj, actualKey, prefixSpaces, yaml, writer);
            }
        }

        String danglingComments = comments.get(null);

        if (danglingComments != null) {
            writer.write(danglingComments);
        }

        writer.close();
    }

    private static void write(Object obj, String actualKey, String prefixSpaces, Yaml yaml, BufferedWriter writer) throws IOException {
        if (obj instanceof ConfigurationSerializable) {
            writer.write(prefixSpaces + actualKey + ": " + yaml.dump(((ConfigurationSerializable) obj).serialize()));
        } else if (obj instanceof String || obj instanceof Character) {
            if (obj instanceof String) {
                String s = (String) obj;
                obj = s.replace("\n", "\\n");
            }

            writer.write(prefixSpaces + actualKey + ": " + yaml.dump(obj));
        } else if (obj instanceof List) {
            writeList((List<?>) obj, actualKey, prefixSpaces, yaml, writer);
        } else {
            writer.write(prefixSpaces + actualKey + ": " + yaml.dump(obj));
        }
    }

    private static void writeSection(BufferedWriter writer, String actualKey, String prefixSpaces,
                                     ConfigurationSection section) throws IOException {

        if (section.getKeys(false).isEmpty()) {
            writer.write(prefixSpaces + actualKey + ": {}");
        } else {
            writer.write(prefixSpaces + actualKey + ":");
        }

        writer.write("\n");
    }

    private static void writeList(List<?> list, String actualKey, String prefixSpaces,
                                  Yaml yaml, BufferedWriter writer) throws IOException {
        writer.write(getListAsString(list, actualKey, prefixSpaces, yaml));
    }

    private static String getListAsString(List<?> list, String actualKey, String prefixSpaces, Yaml yaml) {
        StringBuilder builder = new StringBuilder(prefixSpaces)
                .append(actualKey)
                .append(":");

        if (list.isEmpty()) {
            builder.append(" []\n");
            return builder.toString();
        }

        builder.append("\n");

        for (Object o : list) {
            if (o instanceof String || o instanceof Character) {
                builder.append(prefixSpaces).append("- '").append(o).append("'");
            } else if (o instanceof List) {
                builder.append(prefixSpaces).append("- ").append(yaml.dump(o));
            } else {
                builder.append(prefixSpaces).append("- ").append(o);
            }

            builder.append("\n");
        }

        return builder.toString();
    }

    private static int countIndents(String s) {
        int spaces = 0;

        for (char c : s.toCharArray()) {
            if (c == ' ') {
                spaces++;
            } else {
                break;
            }
        }

        return spaces / 2;
    }

    private static String getPrefixSpaces(int indents) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < indents; i++) {
            builder.append("  ");
        }

        return builder.toString();
    }

    private static void appendPrefixSpaces(StringBuilder builder, int indents) {
        builder.append(getPrefixSpaces(indents));
    }

    public static void updateEventos() {
        for (File file : Objects.requireNonNull(EventoConfigFile.getAllFiles())) {
            if (file.getName().contains("old")) {
                continue;
            }

            YamlConfiguration config = EventoConfigFile.get(file.getName().replace(".yml", ""));

            if (config.getString("Evento.Type") == null) {
                continue;
            }

            if (config.getString("Locations.Server") == null) {
                if (config.getConfigurationSection("Locations") == null) {
                    continue;
                }

                config.set("Locations.Server", "null");

                try {
                    EventoConfigFile.save(config);
                } catch (IOException exception) {
                    Bukkit.getConsoleSender().sendMessage(
                            "§c[AbsolutEvents] Não foi possível atualizar o arquivo de configuração."
                    );
                    exception.printStackTrace();
                }
            }

            if (!EventoType.isEventoChat(EventoType.getEventoType(config.getString("Evento.Type")))) {
                if (!config.isSet("Rewards.Money")) {
                    config.set("Rewards.Money", 1000);

                    try {
                        EventoConfigFile.save(config);
                    } catch (IOException exception) {
                        Bukkit.getConsoleSender().sendMessage(
                                "§c[AbsolutEvents] Não foi possível atualizar o arquivo de configuração."
                        );
                        exception.printStackTrace();
                    }
                }
            }

            SerializerConverter.convert(config);
        }
    }
}