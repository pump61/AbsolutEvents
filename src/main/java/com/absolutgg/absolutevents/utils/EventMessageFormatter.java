package com.absolutgg.absolutevents.utils;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EventMessageFormatter {

    private EventMessageFormatter() {
    }

    @NotNull
    public static List<String> getBroadcast(
            @NotNull YamlConfiguration eventConfig,
            int players,
            int broadcasts,
            double money
    ) {
        FileConfiguration mainConfig = AbsolutEventsPlugin.getInstance().getConfig();

        String globalMode = mainConfig.getString("Broadcast.Mode", "AUTO");
        String eventMode = eventConfig.getString("Broadcast.Mode");

        String mode;
        if (globalMode.equalsIgnoreCase("GLOBAL")) {
            mode = "GLOBAL";
        } else if (globalMode.equalsIgnoreCase("EVENT")) {
            mode = "EVENT";
        } else {
            mode = (eventMode == null || eventMode.isBlank()) ? "AUTO" : eventMode;
        }

        if (mode.equalsIgnoreCase("EVENT")) {
            List<String> eventLines = eventConfig.getStringList("Messages.Broadcast");
            return applyEventBroadcastPlaceholders(eventLines, eventConfig, players, broadcasts, money);
        }

        if (mode.equalsIgnoreCase("GLOBAL")) {
            return buildGlobalBroadcast(mainConfig, eventConfig, players, broadcasts, money);
        }

        List<String> eventLines = eventConfig.getStringList("Messages.Broadcast");
        if (eventConfig.contains("Messages.Broadcast") && !eventLines.isEmpty()) {
            return applyEventBroadcastPlaceholders(eventLines, eventConfig, players, broadcasts, money);
        }

        return buildGlobalBroadcast(mainConfig, eventConfig, players, broadcasts, money);
    }

    @NotNull
    public static String formatConfigMessage(@NotNull String path, @NotNull String message) {
        FileConfiguration mainConfig = AbsolutEventsPlugin.getInstance().getConfig();

        if (!mainConfig.getBoolean("MessageFormat.Enabled", true)) {
            return message;
        }

        String mode = mainConfig.getString("MessageFormat.ConfigMessagesMode", "GLOBAL");
        if (mode.equalsIgnoreCase("RAW")) {
            return message;
        }

        if (shouldIgnore(path, mainConfig)) {
            return message;
        }

        return applyGlobalFormat(message, mainConfig);
    }

    @NotNull
    private static List<String> buildGlobalBroadcast(
            @NotNull FileConfiguration mainConfig,
            @NotNull YamlConfiguration eventConfig,
            int players,
            int broadcasts,
            double money
    ) {
        List<String> globalLines = mainConfig.getStringList("Broadcast.Global");
        if (globalLines.isEmpty()) {
            return Collections.emptyList();
        }

        return applyEventBroadcastPlaceholders(globalLines, eventConfig, players, broadcasts, money);
    }

    @NotNull
    private static List<String> applyEventBroadcastPlaceholders(
            @NotNull List<String> lines,
            @NotNull YamlConfiguration eventConfig,
            int players,
            int broadcasts,
            double money
    ) {
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>(lines.size());

        String rewardsBroadcast = getRewardsBroadcast(eventConfig);

        for (String raw : lines) {
            String line = raw
                    .replace("@players", String.valueOf(players))
                    .replace("@broadcasts", String.valueOf(broadcasts))
                    .replace("@name", eventConfig.getString("Evento.Title", "Evento"))
                    .replace("@money", NumberFormatter.letterFormat(money));

            if (eventConfig.contains("Evento.Cost")) {
                line = line.replace("@cost", NumberFormatter.letterFormat(eventConfig.getDouble("Evento.Cost")));
            }

            if (!rewardsBroadcast.isEmpty()) {
                line = line.replace("@rewards", rewardsBroadcast);
                line = line.replace("@reward", rewardsBroadcast);
            } else if (eventConfig.contains("Evento.Reward")) {
                String reward = NumberFormatter.letterFormat(eventConfig.getDouble("Evento.Reward"));
                line = line.replace("@rewards", reward);
                line = line.replace("@reward", reward);
            } else if (eventConfig.contains("Rewards.Money")) {
                String reward = NumberFormatter.letterFormat(eventConfig.getDouble("Rewards.Money"));
                line = line.replace("@rewards", reward);
                line = line.replace("@reward", reward);
            } else {
                String reward = NumberFormatter.letterFormat(money);
                line = line.replace("@rewards", reward);
                line = line.replace("@reward", reward);
            }

            result.add(line);
        }

        return result;
    }

    @NotNull
    private static String getRewardsBroadcast(@NotNull YamlConfiguration eventConfig) {
        List<String> lines = eventConfig.getStringList("Rewards.Broadcast");

        if (lines == null || lines.isEmpty()) {
            return "";
        }

        return String.join(" ", lines);
    }

    @NotNull
    private static String applyGlobalFormat(@NotNull String message, @NotNull FileConfiguration mainConfig) {
        String baseColor = mainConfig.getString("MessageFormat.BaseColor", "&#ff69f0");
        String valueColor = mainConfig.getString("MessageFormat.ValueColor", "&#0088ff");

        if (message.isBlank()) {
            return message;
        }

        if (startsWithColorCode(message)) {
            return message;
        }

        String formatted = baseColor + message;

        formatted = formatted.replace("@players", valueColor + "@players" + baseColor);
        formatted = formatted.replace("@broadcasts", valueColor + "@broadcasts" + baseColor);
        formatted = formatted.replace("@money", valueColor + "@money" + baseColor);
        formatted = formatted.replace("@cost", valueColor + "@cost" + baseColor);
        formatted = formatted.replace("@rewards", valueColor + "@rewards" + baseColor);
        formatted = formatted.replace("@reward", valueColor + "@reward" + baseColor);
        formatted = formatted.replace("@time", valueColor + "@time" + baseColor);
        formatted = formatted.replace("@position", valueColor + "@position" + baseColor);
        formatted = formatted.replace("@level", valueColor + "@level" + baseColor);
        formatted = formatted.replace("@blocks", valueColor + "@blocks" + baseColor);

        return formatted;
    }

    private static boolean shouldIgnore(@NotNull String path, @NotNull FileConfiguration mainConfig) {
        List<String> ignored = mainConfig.getStringList("MessageFormat.IgnorePaths");
        String lowerPath = path.toLowerCase();

        for (String ignore : ignored) {
            if (lowerPath.contains(ignore.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private static boolean startsWithColorCode(@NotNull String message) {
        String trimmed = message.trim();
        return trimmed.startsWith("&")
                || trimmed.startsWith("§")
                || trimmed.startsWith("&#")
                || trimmed.startsWith("<#");
    }
}