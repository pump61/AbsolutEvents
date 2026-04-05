package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LeagueManager {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final ConnectionManager connectionManager;
    private final YamlConfiguration config;

    public LeagueManager(YamlConfiguration config) {
        this.config = config;
        this.connectionManager = plugin.getConnectionManager();
    }

    public boolean isEnabled() {
        return config.getBoolean("League.Enabled", false);
    }

    public void initializePlayer(Player player) {
        if (!isEnabled() || player == null) {
            return;
        }

        initializePlayer(player.getUniqueId(), player.getName());
    }

    public void initializePlayer(UUID uuid, String username) {
        if (!isEnabled() || uuid == null) {
            return;
        }

        if (connectionManager.hasLeaguePlayer(uuid)) {
            return;
        }

        connectionManager.createLeaguePlayer(
                uuid,
                getDefaultRank(),
                getSeasonId(),
                getStartPoints()
        );
    }

    public int getPoints(Player player) {
        if (player == null) {
            return getMinimumPoints();
        }

        return getPoints(player.getUniqueId());
    }

    public int getPoints(UUID uuid) {
        if (!isEnabled() || uuid == null) {
            return getMinimumPoints();
        }

        ConnectionManager.LeagueData data = connectionManager.getLeagueData(uuid);
        if (data == null) {
            return getStartPoints();
        }

        return Math.max(data.points(), getMinimumPoints());
    }

    public String getRank(Player player) {
        if (player == null) {
            return getDefaultRank();
        }

        return getRank(player.getUniqueId());
    }

    public String getRank(UUID uuid) {
        if (!isEnabled() || uuid == null) {
            return getDefaultRank();
        }

        ConnectionManager.LeagueData data = connectionManager.getLeagueData(uuid);
        if (data == null || data.currentRank() == null || data.currentRank().isBlank()) {
            return resolveRankByPoints(getStartPoints());
        }

        return data.currentRank();
    }

    public String getRankDisplay(Player player) {
        return getRankDisplay(player == null ? null : player.getUniqueId());
    }

    public String getRankDisplay(UUID uuid) {
        String rank = getRank(uuid);
        return config.getString("League.Ranks." + rank + ".Display", rank);
    }

    public String getBadge(Player player) {
        return getBadge(player == null ? null : player.getUniqueId());
    }

    public String getBadge(UUID uuid) {
        String rank = getRank(uuid);
        return config.getString("League.Ranks." + rank + ".Badge", "");
    }

    public int getWins(Player player) {
        return getWins(player == null ? null : player.getUniqueId());
    }

    public int getWins(UUID uuid) {
        if (!isEnabled() || uuid == null) {
            return 0;
        }

        ConnectionManager.LeagueData data = connectionManager.getLeagueData(uuid);
        return data == null ? 0 : data.wins();
    }

    public int getLosses(Player player) {
        return getLosses(player == null ? null : player.getUniqueId());
    }

    public int getLosses(UUID uuid) {
        if (!isEnabled() || uuid == null) {
            return 0;
        }

        ConnectionManager.LeagueData data = connectionManager.getLeagueData(uuid);
        return data == null ? 0 : data.losses();
    }

    public int getPlayed(Player player) {
        return getPlayed(player == null ? null : player.getUniqueId());
    }

    public int getPlayed(UUID uuid) {
        if (!isEnabled() || uuid == null) {
            return 0;
        }

        ConnectionManager.LeagueData data = connectionManager.getLeagueData(uuid);
        return data == null ? 0 : data.played();
    }

    public int getPosition(Player player) {
        return getPosition(player == null ? null : player.getUniqueId());
    }

    public int getPosition(UUID uuid) {
        if (!isEnabled() || uuid == null) {
            return 0;
        }

        List<ConnectionManager.LeagueData> ranking = getCurrentSeasonRanking();

        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }

        return 0;
    }

    public String getNextRank(Player player) {
        return getNextRank(player == null ? null : player.getUniqueId());
    }

    public String getNextRank(UUID uuid) {
        String currentRank = getRank(uuid);
        int currentRequired = getRankRequiredPoints(currentRank);

        List<String> ranks = getOrderedRanks();
        for (String rank : ranks) {
            int required = getRankRequiredPoints(rank);
            if (required > currentRequired) {
                return rank;
            }
        }

        return currentRank;
    }

    public int getPointsToNextRank(Player player) {
        return getPointsToNextRank(player == null ? null : player.getUniqueId());
    }

    public int getPointsToNextRank(UUID uuid) {
        int currentPoints = getPoints(uuid);
        String nextRank = getNextRank(uuid);
        int required = getRankRequiredPoints(nextRank);

        return Math.max(required - currentPoints, 0);
    }

    public void handleSoloWin(Player winner, Collection<Player> losers, String eventKey) {
        if (!isEnabled() || winner == null || !isEventEnabled(eventKey)) {
            return;
        }

        initializePlayers(losers);
        initializePlayer(winner);

        applyWin(winner, eventKey);

        if (isLosePointsEnabled()) {
            for (Player loser : safePlayers(losers)) {
                if (loser.getUniqueId().equals(winner.getUniqueId())) {
                    continue;
                }
                applyLoss(loser, eventKey, false);
            }
        }
    }

    public void handleMultipleWinners(Collection<Player> winners, Collection<Player> losers, String eventKey) {
        if (!isEnabled() || winners == null || winners.isEmpty() || !isEventEnabled(eventKey)) {
            return;
        }

        initializePlayers(winners);
        initializePlayers(losers);

        for (Player winner : safePlayers(winners)) {
            applyWin(winner, eventKey);
        }

        if (isLosePointsEnabled()) {
            for (Player loser : safePlayers(losers)) {
                if (containsPlayer(winners, loser)) {
                    continue;
                }
                applyLoss(loser, eventKey, true);
            }
        }
    }

    public void handleTeamWin(Collection<Player> winners, Collection<Player> losers, String eventKey) {
        if (!isEnabled() || winners == null || winners.isEmpty() || !isEventEnabled(eventKey)) {
            return;
        }

        initializePlayers(winners);
        initializePlayers(losers);

        for (Player winner : safePlayers(winners)) {
            applyWin(winner, eventKey);
        }

        if (isLosePointsEnabled()) {
            for (Player loser : safePlayers(losers)) {
                if (containsPlayer(winners, loser)) {
                    continue;
                }
                applyLoss(loser, eventKey, false);
            }
        }
    }

    public void handleClanWin(Collection<Player> winners, Collection<Player> losers, String eventKey) {
        handleTeamWin(winners, losers, eventKey);
    }

    public void resetSeasonForAll() {
        if (!isEnabled()) {
            return;
        }

        Map<UUID, ConnectionManager.LeagueData> players = connectionManager.getLeaguePlayers();
        String defaultRank = getDefaultRank();
        int startPoints = getStartPoints();
        int newSeasonId = getSeasonId();

        for (ConnectionManager.LeagueData data : players.values()) {
            executeSeasonRewards(data);
            connectionManager.setLeagueSeason(data.uuid(), newSeasonId, startPoints, defaultRank);
        }
    }

    public List<ConnectionManager.LeagueData> getCurrentSeasonRanking() {
        List<ConnectionManager.LeagueData> result = new ArrayList<>();
        Map<UUID, ConnectionManager.LeagueData> all = connectionManager.getLeaguePlayers();

        for (ConnectionManager.LeagueData data : all.values()) {
            if (data.seasonId() == getSeasonId()) {
                result.add(data);
            }
        }

        result.sort(
                Comparator.comparingInt(ConnectionManager.LeagueData::points).reversed()
                        .thenComparingInt(ConnectionManager.LeagueData::wins).reversed()
                        .thenComparingInt(ConnectionManager.LeagueData::losses)
                        .thenComparing(ConnectionManager.LeagueData::username, String.CASE_INSENSITIVE_ORDER)
        );

        return result;
    }

    public List<ConnectionManager.LeagueData> getTopRanking(int limit) {
        List<ConnectionManager.LeagueData> ranking = new ArrayList<>(getCurrentSeasonRanking());

        if (limit <= 0) {
            return ranking;
        }

        if (ranking.size() > limit) {
            return new ArrayList<>(ranking.subList(0, limit));
        }

        return ranking;
    }

    public String getTopName(int position) {
        if (position <= 0) {
            return "-";
        }

        List<ConnectionManager.LeagueData> ranking = getCurrentSeasonRanking();
        if (ranking.size() < position) {
            return "-";
        }

        ConnectionManager.LeagueData data = ranking.get(position - 1);
        return data.username() == null || data.username().isBlank() ? "-" : data.username();
    }

    public int getTopPoints(int position) {
        if (position <= 0) {
            return 0;
        }

        List<ConnectionManager.LeagueData> ranking = getCurrentSeasonRanking();
        if (ranking.size() < position) {
            return 0;
        }

        return ranking.get(position - 1).points();
    }

    public String getTopRank(int position) {
        if (position <= 0) {
            return getDefaultRank();
        }

        List<ConnectionManager.LeagueData> ranking = getCurrentSeasonRanking();
        if (ranking.size() < position) {
            return getDefaultRank();
        }

        String rank = ranking.get(position - 1).currentRank();
        return rank == null || rank.isBlank() ? getDefaultRank() : rank;
    }

    public String getTopRankDisplay(int position) {
        String rank = getTopRank(position);
        return config.getString("League.Ranks." + rank + ".Display", rank);
    }

    public String getTopBadge(int position) {
        String rank = getTopRank(position);
        return config.getString("League.Ranks." + rank + ".Badge", "");
    }

    public String resolveRankByPoints(int points) {
        String selected = getDefaultRank();

        for (String rank : getOrderedRanks()) {
            int required = getRankRequiredPoints(rank);
            if (points >= required) {
                selected = rank;
            }
        }

        return selected;
    }

    private void applyWin(Player player, String eventKey) {
        if (player == null) return;

        initializePlayer(player);

        ConnectionManager.LeagueData current = getOrDefaultData(player);

        int delta = getWinPoints(eventKey);
        int newPoints = Math.max(current.points() + delta, getMinimumPoints());

        String newRank = resolveRankByPoints(newPoints);
        String newPeakRank = getHigherRank(current.peakRank(), newRank);

        connectionManager.updateLeaguePointsAndRank(
                player.getUniqueId(),
                player.getName(),
                newPoints,
                newRank,
                newPeakRank
        );

        connectionManager.updateLeagueProfile(
                player.getUniqueId(),
                player.getName(),
                delta,
                true,
                getMinimumPoints()
        );
    }

    private void applyLoss(Player player, String eventKey, boolean multipleWinners) {
        if (player == null) return;

        initializePlayer(player);

        ConnectionManager.LeagueData current = getOrDefaultData(player);

        int configuredLoss = multipleWinners
                ? getMultipleWinnersLosePoints(eventKey)
                : getLosePoints(eventKey);

        int delta = -Math.max(configuredLoss, 0);
        int newPoints = Math.max(current.points() + delta, getMinimumPoints());

        String newRank = resolveRankByPoints(newPoints);

        connectionManager.updateLeaguePointsAndRank(
                player.getUniqueId(),
                player.getName(),
                newPoints,
                newRank,
                current.peakRank()
        );

        connectionManager.updateLeagueProfile(
                player.getUniqueId(),
                player.getName(),
                delta,
                false,
                getMinimumPoints()
        );
    }

    private void initializePlayers(Collection<Player> players) {
        if (players == null) {
            return;
        }

        for (Player player : players) {
            initializePlayer(player);
        }
    }

    private boolean containsPlayer(Collection<Player> players, Player target) {
        if (players == null || target == null) {
            return false;
        }

        for (Player player : players) {
            if (player != null && player.getUniqueId().equals(target.getUniqueId())) {
                return true;
            }
        }

        return false;
    }

    private List<Player> safePlayers(Collection<Player> players) {
        List<Player> result = new ArrayList<>();
        if (players == null) {
            return result;
        }

        for (Player player : players) {
            if (player != null) {
                result.add(player);
            }
        }

        return result;
    }

    private ConnectionManager.LeagueData getOrDefaultData(Player player) {
        ConnectionManager.LeagueData data = connectionManager.getLeagueData(player.getUniqueId());
        if (data != null) {
            return data;
        }

        return new ConnectionManager.LeagueData(
                player.getUniqueId(),
                player.getName(),
                getStartPoints(),
                0,
                0,
                0,
                getDefaultRank(),
                getDefaultRank(),
                getSeasonId()
        );
    }

    private void executeSeasonRewards(ConnectionManager.LeagueData data) {
        String rank = data.currentRank();
        List<String> commands = config.getStringList("League.Season rewards." + rank + ".Commands");
        if (commands.isEmpty()) {
            return;
        }

        String playerName = data.username() == null || data.username().isBlank() ? data.uuid().toString() : data.username();

        for (String command : commands) {
            String parsed = command.replace("@winner", playerName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private List<String> getOrderedRanks() {
        List<String> ranks = new ArrayList<>();

        ConfigurationSection section = config.getConfigurationSection("League.Ranks");
        if (section == null) {
            return ranks;
        }

        ranks.addAll(section.getKeys(false));
        ranks.sort(Comparator.comparingInt(this::getRankRequiredPoints));
        return ranks;
    }

    private String getHigherRank(String a, String b) {
        int aPoints = getRankRequiredPoints(a);
        int bPoints = getRankRequiredPoints(b);
        return bPoints > aPoints ? b : a;
    }

    private int getRankRequiredPoints(String rank) {
        if (rank == null || rank.isBlank()) {
            return 0;
        }
        return config.getInt("League.Ranks." + rank + ".Points", 0);
    }

    private boolean isEventEnabled(String eventKey) {
        return config.getBoolean("League.Events." + eventKey + ".Enabled", false);
    }

    private int getWinPoints(String eventKey) {
        return Math.max(config.getInt("League.Events." + eventKey + ".Win points", 0), 0);
    }

    private int getLosePoints(String eventKey) {
        return Math.max(config.getInt("League.Events." + eventKey + ".Lose points", 0), 0);
    }

    private int getMultipleWinnersLosePoints(String eventKey) {
        int fallback = getLosePoints(eventKey);
        return Math.max(config.getInt("League.Events." + eventKey + ".Multiple winners lose points", fallback), 0);
    }

    private int getStartPoints() {
        return Math.max(config.getInt("League.Defaults.Start points", 0), 0);
    }

    private int getMinimumPoints() {
        return Math.max(config.getInt("League.Defaults.Minimum points", 0), 0);
    }

    private boolean isLosePointsEnabled() {
        return config.getBoolean("League.Defaults.Lose points enabled", true);
    }

    private int getSeasonId() {
        return Math.max(config.getInt("League.Season.Id", 1), 1);
    }

    public String getDefaultRank() {
        return config.getString("League.Defaults.Default rank", "BRONZE");
    }
}