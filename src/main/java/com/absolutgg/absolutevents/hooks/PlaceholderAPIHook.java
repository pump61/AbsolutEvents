package com.absolutgg.absolutevents.hooks;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PlaceholderAPIHook extends PlaceholderExpansion {

    private final AbsolutEventsPlugin plugin;

    public PlaceholderAPIHook(@NotNull AbsolutEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getIdentifier() {
        return "absolutevents";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        final String normalized = normalize(identifier);

        final Map<String, Integer> wins = plugin.getCacheManager().getPlayerWins(player);
        final Map<String, Integer> participations = plugin.getCacheManager().getPlayerParticipations(player);

        switch (normalized) {
            case "wins":
            case "wins_total":
                return String.valueOf(sumValues(wins));

            case "participations":
            case "participations_total":
                return String.valueOf(sumValues(participations));
        }

        if (normalized.startsWith("wins_")) {
            String eventKey = normalized.substring("wins_".length());
            return String.valueOf(getEventValue(wins, eventKey));
        }

        if (normalized.startsWith("participations_")) {
            String eventKey = normalized.substring("participations_".length());
            return String.valueOf(getEventValue(participations, eventKey));
        }

        return null;
    }

    private int getEventValue(@NotNull Map<String, Integer> values, @NotNull String rawEventKey) {
        String eventKey = normalize(rawEventKey);

        if (!isValidEvent(eventKey)) {
            return 0;
        }

        return values.getOrDefault(eventKey, 0);
    }

    private boolean isValidEvent(@NotNull String eventKey) {
        return getAvailableEventKeys().contains(eventKey);
    }

    private @NotNull Set<String> getAvailableEventKeys() {
        List<File> fileList = EventoConfigFile.getAllFiles();
        if (fileList == null || fileList.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> eventKeys = new HashSet<>();
        for (File file : fileList) {
            String name = file.getName();
            if (!name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                continue;
            }

            String fileName = name.substring(0, name.length() - 4);
            eventKeys.add(normalize(fileName));
        }

        return eventKeys;
    }

    private int sumValues(@NotNull Map<String, Integer> map) {
        return map.values().stream().mapToInt(Integer::intValue).sum();
    }

    private @NotNull String normalize(@NotNull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}