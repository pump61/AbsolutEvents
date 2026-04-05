package com.absolutgg.absolutevents.hooks;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.manager.LeagueManager;
import com.absolutgg.absolutevents.manager.ParkourRecordManager;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
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
        List<String> authors = plugin.getDescription().getAuthors();
        if (authors == null || authors.isEmpty()) {
            return "AbsolutEvents";
        }
        return String.join(", ", authors);
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
        return handleRequest(player, identifier);
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return handleRequest(player, params);
    }

    private @Nullable String handleRequest(@Nullable OfflinePlayer player, @NotNull String identifier) {
        final String normalized = normalize(identifier);

        if (player != null && player.isOnline()) {
            Player onlinePlayer = player.getPlayer();

            if (onlinePlayer != null) {
                final Map<String, Integer> wins = plugin.getCacheManager().getPlayerWins(onlinePlayer);
                final Map<String, Integer> participations = plugin.getCacheManager().getPlayerParticipations(onlinePlayer);

                switch (normalized) {
                    case "wins":
                    case "wins_total":
                        return String.valueOf(sumValues(wins));

                    case "participations":
                    case "participations_total":
                        return String.valueOf(sumValues(participations));

                    case "wins_tournament":
                        return String.valueOf(
                                TournamentStatsManager.getInstance().getWins(onlinePlayer.getUniqueId())
                        );
                }

                if (normalized.startsWith("wins_")) {
                    String eventKey = normalized.substring("wins_".length());
                    return String.valueOf(getEventValue(wins, eventKey));
                }

                if (normalized.startsWith("participations_")) {
                    String eventKey = normalized.substring("participations_".length());
                    return String.valueOf(getEventValue(participations, eventKey));
                }
            }
        }

        LeagueManager league = plugin.getLeagueManager();

        if (league != null && league.isEnabled() && player != null && player.getUniqueId() != null) {
            switch (normalized) {
                case "league_points":
                    return String.valueOf(league.getPoints(player.getUniqueId()));

                case "league_rank":
                    return league.getRank(player.getUniqueId());

                case "league_rank_display":
                    return league.getRankDisplay(player.getUniqueId());

                case "league_badge":
                    return league.getBadge(player.getUniqueId());

                case "league_position":
                    return String.valueOf(league.getPosition(player.getUniqueId()));

                case "league_wins":
                    return String.valueOf(league.getWins(player.getUniqueId()));

                case "league_losses":
                    return String.valueOf(league.getLosses(player.getUniqueId()));

                case "league_played":
                    return String.valueOf(league.getPlayed(player.getUniqueId()));

                case "league_next_rank":
                    return league.getNextRank(player.getUniqueId());

                case "league_points_to_next":
                    return String.valueOf(league.getPointsToNextRank(player.getUniqueId()));
            }
        }

        if (league != null && league.isEnabled()) {
            if (normalized.startsWith("league_top_name_")) {
                Integer pos = parseTopPosition(normalized, "league_top_name_");
                return pos == null ? "-" : league.getTopName(pos);
            }

            if (normalized.startsWith("league_top_points_")) {
                Integer pos = parseTopPosition(normalized, "league_top_points_");
                return pos == null ? "0" : String.valueOf(league.getTopPoints(pos));
            }

            if (normalized.startsWith("league_top_rank_display_")) {
                Integer pos = parseTopPosition(normalized, "league_top_rank_display_");
                return pos == null ? "-" : league.getTopRankDisplay(pos);
            }

            if (normalized.startsWith("league_top_rank_")) {
                Integer pos = parseTopPosition(normalized, "league_top_rank_");
                return pos == null ? league.getDefaultRank() : league.getTopRank(pos);
            }

            if (normalized.startsWith("league_top_badge_")) {
                Integer pos = parseTopPosition(normalized, "league_top_badge_");
                return pos == null ? "" : league.getTopBadge(pos);
            }
        }

        ParkourRecordManager manager = ParkourRecordManager.getInstance();
        String lower = normalized;

        if (lower.startsWith("parkour_besttime_raw_")) {
            String eventKey = identifier.substring("parkour_besttime_raw_".length());
            if (player == null || player.getUniqueId() == null) {
                return "0";
            }
            return String.valueOf(manager.getRecord(eventKey, player.getUniqueId()));
        }

        if (lower.startsWith("parkour_besttime_")) {
            String eventKey = identifier.substring("parkour_besttime_".length());
            return manager.getFormattedRecord(eventKey, player);
        }

        if (lower.startsWith("parkour_besttime_player_")) {
            String rest = identifier.substring("parkour_besttime_player_".length());
            int split = rest.indexOf('_');
            if (split == -1) {
                return "--:--.--";
            }

            String eventKey = rest.substring(0, split);
            String target = rest.substring(split + 1);
            return manager.getFormattedRecord(eventKey, target);
        }

        if (lower.startsWith("parkour_besttime_raw_player_")) {
            String rest = identifier.substring("parkour_besttime_raw_player_".length());
            int split = rest.indexOf('_');
            if (split == -1) {
                return "0";
            }

            String eventKey = rest.substring(0, split);
            String target = rest.substring(split + 1);
            return String.valueOf(manager.getRecord(eventKey, target));
        }

        if (lower.startsWith("parkour_top_name_")) {
            String rest = identifier.substring("parkour_top_name_".length());
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
            String rest = identifier.substring("parkour_top_time_raw_".length());
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
            String rest = identifier.substring("parkour_top_time_".length());
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

    private @Nullable Integer parseTopPosition(@NotNull String identifier, @NotNull String prefix) {
        try {
            return Integer.parseInt(identifier.substring(prefix.length()));
        } catch (Exception ignored) {
            return null;
        }
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
        List<File> files = EventoConfigFile.getAllFiles();
        if (files == null || files.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> eventKeys = new HashSet<>();
        for (File file : files) {
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