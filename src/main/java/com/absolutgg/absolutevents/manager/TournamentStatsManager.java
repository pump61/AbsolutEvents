package com.absolutgg.absolutevents.manager;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TournamentStatsManager {

    private static final TournamentStatsManager INSTANCE = new TournamentStatsManager();

    private final Map<UUID, Integer> wins = new ConcurrentHashMap<>();

    private TournamentStatsManager() {
    }

    public static @NotNull TournamentStatsManager getInstance() {
        return INSTANCE;
    }

    public int getWins(@NotNull UUID uuid) {
        return wins.getOrDefault(uuid, 0);
    }

    public void addWin(@NotNull UUID uuid) {
        wins.merge(uuid, 1, Integer::sum);
    }

    public void setWins(@NotNull UUID uuid, int value) {
        if (value <= 0) {
            wins.remove(uuid);
            return;
        }

        wins.put(uuid, value);
    }

    public void resetPlayer(@NotNull UUID uuid) {
        wins.remove(uuid);
    }

    public void resetAll() {
        wins.clear();
    }

    public @NotNull Map<UUID, Integer> getAllWins() {
        return new ConcurrentHashMap<>(wins);
    }
}