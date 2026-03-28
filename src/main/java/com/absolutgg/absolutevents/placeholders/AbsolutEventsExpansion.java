package com.absolutgg.absolutevents.placeholders;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.managers.ParkourRecordManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AbsolutEventsExpansion extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "absolutevents";
    }

    @Override
    public @NotNull String getAuthor() {
        return "OpenAI";
    }

    @Override
    public @NotNull String getVersion() {
        return AbsolutEventsPlugin.getInstance().getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        ParkourRecordManager manager = ParkourRecordManager.getInstance();
        String lower = params.toLowerCase();

        if (lower.startsWith("parkour_besttime_raw_")) {
            String eventKey = params.substring("parkour_besttime_raw_".length());
            if (player == null || player.getUniqueId() == null) {
                return "0";
            }
            return String.valueOf(manager.getRecord(eventKey, player.getUniqueId()));
        }

        if (lower.startsWith("parkour_besttime_")) {
            String eventKey = params.substring("parkour_besttime_".length());
            return manager.getFormattedRecord(eventKey, player);
        }

        if (lower.startsWith("parkour_besttime_player_")) {
            String rest = params.substring("parkour_besttime_player_".length());
            int split = rest.indexOf('_');
            if (split == -1) {
                return "--:--.--";
            }

            String eventKey = rest.substring(0, split);
            String target = rest.substring(split + 1);
            return manager.getFormattedRecord(eventKey, target);
        }

        if (lower.startsWith("parkour_besttime_raw_player_")) {
            String rest = params.substring("parkour_besttime_raw_player_".length());
            int split = rest.indexOf('_');
            if (split == -1) {
                return "0";
            }

            String eventKey = rest.substring(0, split);
            String target = rest.substring(split + 1);
            return String.valueOf(manager.getRecord(eventKey, target));
        }

        if (lower.startsWith("parkour_top_name_")) {
            String rest = params.substring("parkour_top_name_".length());
            int split = rest.lastIndexOf('_');
            if (split == -1) {
                return "-";
            }

            String eventKey = rest.substring(0, split);
            try {
                int pos = Integer.parseInt(rest.substring(split + 1));
                return manager.getTopName(eventKey, pos);
            } catch (NumberFormatException ignored) {
                return "-";
            }
        }

        if (lower.startsWith("parkour_top_time_raw_")) {
            String rest = params.substring("parkour_top_time_raw_".length());
            int split = rest.lastIndexOf('_');
            if (split == -1) {
                return "0";
            }

            String eventKey = rest.substring(0, split);
            try {
                int pos = Integer.parseInt(rest.substring(split + 1));
                return String.valueOf(manager.getTopRaw(eventKey, pos));
            } catch (NumberFormatException ignored) {
                return "0";
            }
        }

        if (lower.startsWith("parkour_top_time_")) {
            String rest = params.substring("parkour_top_time_".length());
            int split = rest.lastIndexOf('_');
            if (split == -1) {
                return "--:--.--";
            }

            String eventKey = rest.substring(0, split);
            try {
                int pos = Integer.parseInt(rest.substring(split + 1));
                return manager.getTopFormatted(eventKey, pos);
            } catch (NumberFormatException ignored) {
                return "--:--.--";
            }
        }

        return null;
    }
}