package com.absolutgg.absolutevents.hooks;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;
import java.util.Objects;

public final class PlaceholderAPIHook extends PlaceholderExpansion {

    private final AbsolutEventsPlugin plugin;

    public PlaceholderAPIHook(AbsolutEventsPlugin plugin) {
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
        return "aeventos";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {

        if (player == null) {
            return "";
        }

        if (identifier.equalsIgnoreCase("wins_total")) {
            Map<String, Integer> wins = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerWins(player);
            int value = wins.values().stream().mapToInt(Integer::intValue).sum();
            return String.valueOf(value);
        }

        if (identifier.equalsIgnoreCase("participations_total")) {
            Map<String, Integer> participations = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerParticipations(player);
            int value = participations.values().stream().mapToInt(Integer::intValue).sum();
            return String.valueOf(value);
        }

        if (identifier.startsWith("wins_")) {
            for (File config : Objects.requireNonNull(EventoConfigFile.getAllFiles())) {
                String fileName = config.getName().substring(0, config.getName().length() - 4);

                if (identifier.equalsIgnoreCase("wins_" + fileName)) {
                    Map<String, Integer> wins = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerWins(player);
                    return String.valueOf(wins.getOrDefault(fileName, 0));
                }
            }

            return "0";
        }

        if (identifier.startsWith("participations_")) {
            for (File config : Objects.requireNonNull(EventoConfigFile.getAllFiles())) {
                String fileName = config.getName().substring(0, config.getName().length() - 4);

                if (identifier.equalsIgnoreCase("participations_" + fileName)) {
                    Map<String, Integer> participations = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerParticipations(player);
                    return String.valueOf(participations.getOrDefault(fileName, 0));
                }
            }

            return "0";
        }

        return null;
    }
}