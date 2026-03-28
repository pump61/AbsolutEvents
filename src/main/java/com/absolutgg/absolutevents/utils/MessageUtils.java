package com.absolutgg.absolutevents.utils;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MessageUtils {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private MessageUtils() {
    }

    @NotNull
    public static String getMessage(@NotNull String path) {
        return getMessage(path, Collections.emptyMap());
    }

    @NotNull
    public static String getMessage(@NotNull String path, @Nullable Map<String, String> placeholders) {
        FileConfiguration config = AbsolutEventsPlugin.getInstance().getConfig();

        String prefix = config.getString("Messages.Prefix", "&#ff69f0[Evento] ");
        String message = config.getString(path, "&cMensagem não encontrada: " + path);

        String result = prefix + message;

        if (placeholders != null && !placeholders.isEmpty()) {
            result = replacePlaceholders(result, placeholders);
        }

        result = EventMessageFormatter.formatConfigMessage(path, result);
        return ColorUtils.colorize(result);
    }

    @NotNull
    public static List<String> getMessageList(@NotNull String path) {
        return getMessageList(path, Collections.emptyMap(), false);
    }

    @NotNull
    public static List<String> getMessageList(@NotNull String path, boolean withPrefix) {
        return getMessageList(path, Collections.emptyMap(), withPrefix);
    }

    @NotNull
    public static List<String> getMessageList(
            @NotNull String path,
            @Nullable Map<String, String> placeholders,
            boolean withPrefix
    ) {
        FileConfiguration config = AbsolutEventsPlugin.getInstance().getConfig();
        List<String> messages = config.getStringList(path);

        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        String prefix = config.getString("Messages.Prefix", "&#ff69f0[Evento] ");
        List<String> result = new java.util.ArrayList<>(messages.size());

        for (String line : messages) {
            String formatted = withPrefix ? prefix + line : line;

            if (placeholders != null && !placeholders.isEmpty()) {
                formatted = replacePlaceholders(formatted, placeholders);
            }

            formatted = EventMessageFormatter.formatConfigMessage(path, formatted);
            result.add(ColorUtils.colorize(formatted));
        }

        return result;
    }

    @NotNull
    public static Component component(@NotNull String text) {
        return LEGACY.deserialize(ColorUtils.colorize(text));
    }

    @NotNull
    public static Component getComponent(@NotNull String path) {
        return component(getMessage(path));
    }

    @NotNull
    public static Component getComponent(@NotNull String path, @Nullable Map<String, String> placeholders) {
        return component(getMessage(path, placeholders));
    }

    @NotNull
    public static List<Component> getComponentList(@NotNull String path) {
        return getComponentList(path, Collections.emptyMap(), false);
    }

    @NotNull
    public static List<Component> getComponentList(@NotNull String path, boolean withPrefix) {
        return getComponentList(path, Collections.emptyMap(), withPrefix);
    }

    @NotNull
    public static List<Component> getComponentList(
            @NotNull String path,
            @Nullable Map<String, String> placeholders,
            boolean withPrefix
    ) {
        List<String> lines = getMessageList(path, placeholders, withPrefix);
        List<Component> components = new java.util.ArrayList<>(lines.size());

        for (String line : lines) {
            components.add(component(line));
        }

        return components;
    }

    public static void send(@NotNull CommandSender sender, @NotNull String path) {
        sender.sendMessage(getComponent(path));
    }

    public static void send(
            @NotNull CommandSender sender,
            @NotNull String path,
            @Nullable Map<String, String> placeholders
    ) {
        sender.sendMessage(getComponent(path, placeholders));
    }

    public static void sendRaw(@NotNull CommandSender sender, @NotNull String text) {
        sender.sendMessage(component(text));
    }

    public static void sendList(@NotNull CommandSender sender, @NotNull String path) {
        sendList(sender, path, Collections.emptyMap(), false);
    }

    public static void sendList(
            @NotNull CommandSender sender,
            @NotNull String path,
            @Nullable Map<String, String> placeholders,
            boolean withPrefix
    ) {
        for (Component line : getComponentList(path, placeholders, withPrefix)) {
            sender.sendMessage(line);
        }
    }

    public static void broadcast(@NotNull String path) {
        Bukkit.broadcast(getComponent(path));
    }

    public static void broadcast(@NotNull String path, @Nullable Map<String, String> placeholders) {
        Bukkit.broadcast(getComponent(path, placeholders));
    }

    public static void broadcastRaw(@NotNull String text) {
        Bukkit.broadcast(component(text));
    }

    @NotNull
    public static String replacePlaceholders(
            @NotNull String text,
            @Nullable Map<String, String> placeholders
    ) {
        if (placeholders == null || placeholders.isEmpty()) {
            return text;
        }

        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String angleKey = "<" + entry.getKey() + ">";
            String atKey = "@" + entry.getKey();
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());

            result = result.replace(angleKey, value);
            result = result.replace(atKey, value);
        }

        return result;
    }

    @NotNull
    public static Map<String, String> placeholder(@NotNull String key, @Nullable Object value) {
        return Map.of(key, value == null ? "" : String.valueOf(value));
    }

    @NotNull
    public static Map<String, String> placeholders(@NotNull Object... values) {
        if (values.length == 0) {
            return Collections.emptyMap();
        }

        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Os placeholders devem ser passados em pares: chave, valor.");
        }

        Map<String, String> placeholders = new java.util.HashMap<>();

        for (int index = 0; index < values.length; index += 2) {
            String key = String.valueOf(values[index]);
            Object value = values[index + 1];
            placeholders.put(key, value == null ? "" : String.valueOf(value));
        }

        return placeholders;
    }

    public static void sendActionBar(@NotNull Player player, @NotNull String text) {
        player.sendActionBar(component(text));
    }
}