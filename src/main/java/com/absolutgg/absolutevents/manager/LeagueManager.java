package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LeagueManager {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;

    public LeagueManager(YamlConfiguration config) {
        this.config = config;
    }

    private ConnectionManager connectionManager() {
        return plugin.getConnectionManager();
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

        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
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

        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
            return getStartPoints();
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

        return resolveRankByPoints(getPoints(uuid));
    }

    public String getRankDisplay(Player player) {
        return getRankDisplay(player == null ? null : player.getUniqueId());
    }

    public String getRankDisplay(UUID uuid) {
        return getRankDisplayById(getRank(uuid));
    }

    public String getBadge(Player player) {
        return getBadge(player == null ? null : player.getUniqueId());
    }

    public String getBadge(UUID uuid) {
        return getBadgeById(getRank(uuid));
    }

    public String getRankBase(Player player) {
        return getRankBase(player == null ? null : player.getUniqueId());
    }

    public String getRankBase(UUID uuid) {
        return extractBaseRank(getRank(uuid));
    }

    public String getRankTier(Player player) {
        return getRankTier(player == null ? null : player.getUniqueId());
    }

    public String getRankTier(UUID uuid) {
        return extractTier(getRank(uuid));
    }

    public int getWins(Player player) {
        return getWins(player == null ? null : player.getUniqueId());
    }

    public int getWins(UUID uuid) {
        if (!isEnabled() || uuid == null) {
            return 0;
        }

        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
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

        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
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

        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
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

        for (String rankId : getOrderedRanks()) {
            int required = getRankRequiredPoints(rankId);
            if (required > currentRequired) {
                return rankId;
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

        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
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

        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
            return result;
        }

        Map<UUID, ConnectionManager.LeagueData> all = connectionManager.getLeaguePlayers();

        for (ConnectionManager.LeagueData data : all.values()) {
            if (data.seasonId() == getSeasonId()) {
                result.add(data);
            }
        }

        result.sort((a, b) -> {
            int comparePoints = Integer.compare(b.points(), a.points());
            if (comparePoints != 0) {
                return comparePoints;
            }

            int compareWins = Integer.compare(b.wins(), a.wins());
            if (compareWins != 0) {
                return compareWins;
            }

            int compareLosses = Integer.compare(a.losses(), b.losses());
            if (compareLosses != 0) {
                return compareLosses;
            }

            String aName = a.username() == null ? "" : a.username();
            String bName = b.username() == null ? "" : b.username();
            return aName.compareToIgnoreCase(bName);
        });

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

        return resolveRankByPoints(ranking.get(position - 1).points());
    }

    public String getTopRankDisplay(int position) {
        return getRankDisplayById(getTopRank(position));
    }

    public String getTopBadge(int position) {
        return getBadgeById(getTopRank(position));
    }

    public String resolveRankByPoints(int points) {
        String selected = getDefaultRank();

        for (String rankId : getOrderedRanks()) {
            int required = getRankRequiredPoints(rankId);
            if (points >= required) {
                selected = rankId;
            }
        }

        return selected;
    }

    private void applyWin(Player player, String eventKey) {
        if (player == null) {
            return;
        }

        initializePlayer(player);

        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
            return;
        }

        ConnectionManager.LeagueData current = getOrDefaultData(player);
        int delta = getWinPoints(eventKey);
        int newPoints = Math.max(current.points() + delta, getMinimumPoints());

        String newRank = resolveRankByPoints(newPoints);
        String currentPeak = current.peakRank() == null || current.peakRank().isBlank()
                ? getDefaultRank()
                : current.peakRank();
        String newPeakRank = getHigherRank(currentPeak, newRank);

        connectionManager.updateLeagueProfile(
                player.getUniqueId(),
                player.getName(),
                delta,
                true,
                getMinimumPoints()
        );

        connectionManager.updateLeaguePointsAndRank(
                player.getUniqueId(),
                player.getName(),
                newPoints,
                newRank,
                newPeakRank
        );
    }

    private void applyLoss(Player player, String eventKey, boolean multipleWinners) {
        if (player == null) {
            return;
        }

        initializePlayer(player);

        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
            return;
        }

        ConnectionManager.LeagueData current = getOrDefaultData(player);
        int configuredLoss = multipleWinners
                ? getMultipleWinnersLosePoints(eventKey)
                : getLosePoints(eventKey);

        int delta = -Math.max(configuredLoss, 0);
        int newPoints = Math.max(current.points() + delta, getMinimumPoints());
        String newRank = resolveRankByPoints(newPoints);

        connectionManager.updateLeagueProfile(
                player.getUniqueId(),
                player.getName(),
                delta,
                false,
                getMinimumPoints()
        );

        connectionManager.updateLeaguePointsAndRank(
                player.getUniqueId(),
                player.getName(),
                newPoints,
                newRank,
                current.peakRank() == null || current.peakRank().isBlank() ? getDefaultRank() : current.peakRank()
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
        ConnectionManager connectionManager = connectionManager();
        if (connectionManager == null) {
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
        String currentRank = data.currentRank();
        String baseRank = extractBaseRank(currentRank);

        List<String> commands = config.getStringList("League.Season rewards." + currentRank + ".Commands");
        if (commands.isEmpty()) {
            commands = config.getStringList("League.Season rewards." + baseRank + ".Commands");
        }

        if (commands.isEmpty()) {
            return;
        }

        String playerName = data.username() == null || data.username().isBlank()
                ? data.uuid().toString()
                : data.username();

        for (String command : commands) {
            String parsed = command.replace("@winner", playerName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private List<String> getOrderedRanks() {
        List<String> ranks = new ArrayList<>();

        ConfigurationSection ranksSection = config.getConfigurationSection("League.Ranks");
        if (ranksSection == null) {
            return ranks;
        }

        for (String baseRank : ranksSection.getKeys(false)) {
            ConfigurationSection tiersSection = config.getConfigurationSection("League.Ranks." + baseRank + ".Tiers");
            if (tiersSection == null) {
                continue;
            }

            for (String tier : tiersSection.getKeys(false)) {
                ranks.add(baseRank + "_" + tier);
            }
        }

        ranks.sort((a, b) -> Integer.compare(getRankRequiredPoints(a), getRankRequiredPoints(b)));
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

        String baseRank = extractBaseRank(rank);
        String tier = extractTier(rank);

        if (baseRank.isBlank() || tier.isBlank()) {
            return 0;
        }

        return config.getInt("League.Ranks." + baseRank + ".Tiers." + tier + ".Points", 0);
    }

    private String getRankDisplayById(String rank) {
        if (rank == null || rank.isBlank()) {
            return getDefaultRank();
        }

        String baseRank = extractBaseRank(rank);
        String tier = extractTier(rank);

        if (baseRank.isBlank() || tier.isBlank()) {
            return rank;
        }

        return config.getString("League.Ranks." + baseRank + ".Tiers." + tier + ".Display", rank);
    }

    private String getBadgeById(String rank) {
        if (rank == null || rank.isBlank()) {
            return "";
        }

        String baseRank = extractBaseRank(rank);
        String tier = extractTier(rank);

        if (baseRank.isBlank() || tier.isBlank()) {
            return "";
        }

        return config.getString("League.Ranks." + baseRank + ".Tiers." + tier + ".Badge", "");
    }

    private String extractBaseRank(String rank) {
        if (rank == null || rank.isBlank()) {
            return "";
        }

        int separator = rank.lastIndexOf('_');
        if (separator == -1) {
            return rank.toUpperCase();
        }

        return rank.substring(0, separator).toUpperCase();
    }

    private String extractTier(String rank) {
        if (rank == null || rank.isBlank()) {
            return "";
        }

        int separator = rank.lastIndexOf('_');
        if (separator == -1 || separator + 1 >= rank.length()) {
            return "";
        }

        return rank.substring(separator + 1);
    }

    public String getRankDisplayByIdPublic(String rank) {
        return getRankDisplayById(rank);
    }

    public String getBadgeByIdPublic(String rank) {
        return getBadgeById(rank);
    }

    public String getRankBaseByIdPublic(String rank) {
        return extractBaseRank(rank);
    }

    public String getRankTierByIdPublic(String rank) {
        return extractTier(rank);
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
        String configured = config.getString("League.Defaults.Default rank", "").trim();

        if (!configured.isBlank()) {
            if (configured.contains("_")) {
                return configured.toUpperCase();
            }

            ConfigurationSection tiersSection = config.getConfigurationSection("League.Ranks." + configured.toUpperCase() + ".Tiers");
            if (tiersSection != null) {
                String firstTier = null;
                int lowest = Integer.MAX_VALUE;

                for (String tier : tiersSection.getKeys(false)) {
                    int points = config.getInt("League.Ranks." + configured.toUpperCase() + ".Tiers." + tier + ".Points", 0);
                    if (points < lowest) {
                        lowest = points;
                        firstTier = tier;
                    }
                }

                if (firstTier != null) {
                    return configured.toUpperCase() + "_" + firstTier;
                }
            }
        }

        List<String> ordered = getOrderedRanks();
        if (!ordered.isEmpty()) {
            return ordered.get(0);
        }

        return "UNRANKED_I";
    }
}