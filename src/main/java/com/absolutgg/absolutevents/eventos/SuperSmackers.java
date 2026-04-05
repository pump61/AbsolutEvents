package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.SuperSmackersListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.EventKitApplier;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class SuperSmackers extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final SuperSmackersListener listener = new SuperSmackersListener();

    private final String mode;
    private final int roundCountdown;
    private final int roundMaxTime;
    private final int nextRoundDelay;

    private final double knockbackHorizontal;
    private final double knockbackVertical;

    private final boolean duoAutoPair;
    private final boolean duoPreferClans;
    private final boolean duoEliminateOdd;

    private final double jumpPower;
    private final int jumpCooldownSeconds;
    private final int speedAmplifier;
    private final int speedDuration;
    private final int speedCooldownSeconds;

    private final List<ArenaData> arenas = new ArrayList<>();
    private final List<TeamData> teams = new ArrayList<>();
    private final Map<Player, Integer> soloPoints = new HashMap<>();
    private final Map<Player, String> playerColors = new HashMap<>();
    private final Map<UUID, Long> jumpCooldown = new HashMap<>();
    private final Map<UUID, Long> speedCooldown = new HashMap<>();
    private final List<ClanPlayer> simpleClansPlayers = new ArrayList<>();
    private final Map<UUID, UUID> duoInvites = new HashMap<>();

    private final List<Material> jumpPadMaterials = new ArrayList<>();
    private final List<Material> speedPadMaterials = new ArrayList<>();
    private final List<String> availableSoloColors = new ArrayList<>();
    private final List<String> availableTeamColors = new ArrayList<>();

    private final List<SoloMatch> soloMatches = new ArrayList<>();
    private final List<SoloMatch> tieBreakSoloMatches = new ArrayList<>();

    private final List<DuoMatch> duoMatches = new ArrayList<>();
    private final List<DuoMatch> tieBreakDuoMatches = new ArrayList<>();

    private int currentRound = 0;
    private boolean roundRunning = false;
    private boolean roundStarting = false;
    private boolean eventEnding = false;
    private boolean tieBreakMode = false;

    private ArenaData currentArena;
    private Player currentP1;
    private Player currentP2;
    private TeamData currentTeam1;
    private TeamData currentTeam2;

    private DuoMatch currentDuoMatch;
    private int currentDuoLeg = 0;
    private TeamData currentDuoLegWinnerTeam;

    private BukkitTask roundTask;
    private BukkitTask countdownTask;
    private BukkitTask actionbarTask;
    private BukkitTask nextRoundTask;

    public SuperSmackers(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.mode = config.getString("Evento.Mode", "SOLO").toUpperCase(Locale.ROOT).replace("#", "").trim();
        this.roundCountdown = Math.max(1, config.getInt("Evento.Round countdown", 3));
        this.roundMaxTime = Math.max(5, config.getInt("Evento.Round max time", 60));
        this.nextRoundDelay = Math.max(1, config.getInt("Evento.Next round delay", 10));

        this.knockbackHorizontal = config.getDouble("Evento.Knockback horizontal", 1.35D);
        this.knockbackVertical = config.getDouble("Evento.Knockback vertical", 0.55D);

        this.duoAutoPair = config.getBoolean("Evento.Duo auto pair", true);
        this.duoPreferClans = config.getBoolean("Evento.Duo prefer clans", true);
        this.duoEliminateOdd = config.getBoolean("Evento.Duo eliminate odd player", true);

        this.jumpPower = config.getDouble("Pads.Jump.Power", 1.25D);
        this.jumpCooldownSeconds = Math.max(1, config.getInt("Pads.Jump.Cooldown", 8));
        this.speedAmplifier = Math.max(0, config.getInt("Pads.Speed.Amplifier", 2));
        this.speedDuration = Math.max(1, config.getInt("Pads.Speed.Duration", 4));
        this.speedCooldownSeconds = Math.max(1, config.getInt("Pads.Speed.Cooldown", 10));

        loadPads();
        loadArenas();
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        resetState();

        prepareAvailableColors();

        if (!hasEnoughColors()) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    "&#ff4444SuperSmackers não pode iniciar: faltam cores suficientes em &fTeam colors&#ff4444."
            ));
            stop();
            return;
        }

        restoreRoundVisibility();

        if (isSolo()) {
            for (Player player : getPlayers()) {
                String uniqueColor = takeNextSoloColor();
                if (uniqueColor == null) {
                    Bukkit.broadcastMessage(ColorUtils.colorize(
                            "&#ff4444SuperSmackers não pode iniciar: faltam cores únicas para os jogadores."
                    ));
                    stop();
                    return;
                }

                soloPoints.put(player, 0);
                playerColors.put(player, uniqueColor);
                prepareAsLobbyPlayer(player);
            }
        } else {
            for (Player player : getPlayers()) {
                prepareAsLobbyPlayer(player);
            }
        }

        for (Player spectator : getSpectators()) {
            prepareAsGlobalSpectator(spectator);
        }

        setupFriendlyFire();

        for (String line : config.getStringList("Messages.Start")) {
            sendToEvent(line.replace("@name", config.getString("Evento.Title", "Super Smackers")));
        }

        if (!isSolo()) {
            for (String line : config.getStringList("Messages.Duo help")) {
                sendToEvent(line);
            }
        }

        prepareModeData();

        if (isSolo()) {
            generateSoloMatches();
        } else {
            if (teams.size() < 2) {
                for (String line : config.getStringList("Messages.No players")) {
                    Bukkit.broadcastMessage(ColorUtils.colorize(line));
                }
                stop();
                return;
            }
            generateDuoMatches();
        }

        startActionbar();
        scheduleNextRound(nextRoundDelay);
    }

    @Override
    public void stop() {
        cancelTask(roundTask);
        cancelTask(countdownTask);
        cancelTask(actionbarTask);
        cancelTask(nextRoundTask);

        restoreRoundVisibility();

        for (ClanPlayer clanPlayer : simpleClansPlayers) {
            clanPlayer.setFriendlyFire(false);
        }

        simpleClansPlayers.clear();

        for (Player player : new ArrayList<>(getPlayers())) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            clearPlayer(player);
        }

        for (Player spectator : new ArrayList<>(getSpectators())) {
            spectator.setGameMode(GameMode.SURVIVAL);
            spectator.setAllowFlight(false);
            spectator.setFlying(false);
            clearPlayer(spectator);
        }

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    @Override
    public void leave(Player player) {
        if (!getPlayers().contains(player)) {
            super.leave(player);
            return;
        }

        restoreRoundVisibility();

        boolean wasFighting = player.equals(currentP1) || player.equals(currentP2);

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        clearPlayer(player);
        removePendingInvites(player);

        if (isSolo()) {
            processSoloForfeit(player);
            soloPoints.remove(player);
            playerColors.remove(player);
            remove(player);

            if (wasFighting) {
                handleCombatLeave(player);
                return;
            }
        } else {
            TeamData team = getTeam(player);
            processDuoForfeit(player);

            if (team != null) {
                for (Player member : team.members) {
                    if (!member.equals(player)) {
                        sendConfigMessage(member, "Messages.Duo partner left", placeholder("@player", player.getName()));
                    }
                }
            }

            if (wasFighting) {
                handleCombatLeave(player);
                return;
            }
        }

        if (isHappening() && !eventEnding) {
            checkEndConditions();
        }
    }

    @Override
    public void winner(Player player) {
        if (eventEnding) {
            return;
        }

        eventEnding = true;

        List<Player> losers = new ArrayList<>(getPlayers());
        losers.removeIf(target -> target.getUniqueId().equals(player.getUniqueId()));

        for (String line : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    line.replace("@name", config.getString("Evento.Title", "Super Smackers"))
                            .replace("@winner", player.getName())
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(
                player.getName(),
                config.getString("Evento.Title", "Super Smackers")
        );

        setWinner(player);

        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        if (plugin.getLeagueManager() != null) {
            plugin.getLeagueManager().handleSoloWin(
                    player,
                    losers,
                    "supersmackers"
            );
        }

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }

        stop();
    }

    public void teamWinner(TeamData team) {
        if (eventEnding) {
            return;
        }

        eventEnding = true;

        for (String line : config.getStringList("Messages.Team winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    line.replace("@name", config.getString("Evento.Title", "Super Smackers"))
                            .replace("@team", team.getDisplayName())
            ));
        }

        if (!team.members.isEmpty()) {
            setWinners(new HashSet<>(team.members));
        }

        for (Player player : team.members) {
            TournamentStatsManager.getInstance().addWin(player.getUniqueId());
        }

        DiscordWebhookManager.sendTeamWinnerWithPlayers(
                team.getDisplayName(),
                config.getString("Evento.Title"),
                team.members.stream().map(Player::getName).toList()
        );

        for (Player player : team.members) {
            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(player, command.replace("@winner", player.getName()));
            }
        }

        stop();
    }

    public boolean isDuoMode() {
        return !isSolo();
    }

    public boolean inviteDuo(Player inviter, Player target) {
        if (isSolo()) {
            inviter.sendMessage(ColorUtils.colorize("&#ff4444Esse evento não está em modo duo."));
            return false;
        }

        if (!isOpen()) {
            inviter.sendMessage(ColorUtils.colorize("&#ff4444As duplas só podem ser definidas durante as chamadas."));
            return false;
        }

        if (inviter.equals(target)) {
            inviter.sendMessage(ColorUtils.colorize("&#ff4444Você não pode se convidar."));
            return false;
        }

        if (!getPlayers().contains(inviter) || !getPlayers().contains(target)) {
            inviter.sendMessage(ColorUtils.colorize("&#ff4444Os dois jogadores precisam estar no evento."));
            return false;
        }

        if (hasTeam(inviter)) {
            sendConfigMessage(inviter, "Messages.Duo already has team");
            return false;
        }

        if (hasTeam(target)) {
            sendConfigMessage(inviter, "Messages.Duo target already has team");
            return false;
        }

        duoInvites.put(target.getUniqueId(), inviter.getUniqueId());

        sendConfigMessage(inviter, "Messages.Duo invite sent", placeholder("@player", target.getName()));
        sendConfigMessage(target, "Messages.Duo invite received", placeholder("@player", inviter.getName()));
        return true;
    }

    public boolean acceptDuo(Player player) {
        if (isSolo()) {
            player.sendMessage(ColorUtils.colorize("&#ff4444Esse evento não está em modo duo."));
            return false;
        }

        UUID inviterId = duoInvites.remove(player.getUniqueId());
        if (inviterId == null) {
            sendConfigMessage(player, "Messages.Duo no pending invite");
            return false;
        }

        Player inviter = Bukkit.getPlayer(inviterId);
        if (inviter == null || !inviter.isOnline() || !getPlayers().contains(inviter)) {
            sendConfigMessage(player, "Messages.Duo no pending invite");
            return false;
        }

        if (hasTeam(player) || hasTeam(inviter)) {
            sendConfigMessage(player, "Messages.Duo already has team");
            return false;
        }

        createTeam(inviter, player);
        removePendingInvites(inviter);
        removePendingInvites(player);

        sendConfigMessage(player, "Messages.Duo accepted", placeholder("@player", inviter.getName()));
        sendConfigMessage(inviter, "Messages.Duo accepted", placeholder("@player", player.getName()));
        return true;
    }

    public boolean declineDuo(Player player) {
        UUID inviterId = duoInvites.remove(player.getUniqueId());
        if (inviterId == null) {
            sendConfigMessage(player, "Messages.Duo no pending invite");
            return false;
        }

        Player inviter = Bukkit.getPlayer(inviterId);
        sendConfigMessage(player, "Messages.Duo declined", placeholder("@player", inviter != null ? inviter.getName() : "desconhecido"));

        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(ColorUtils.colorize("&#ff4444" + player.getName() + " recusou seu convite de dupla."));
        }
        return true;
    }

    public boolean hasTeam(Player player) {
        return getTeam(player) != null;
    }

    public void handleRoundLose(Player loser) {
        if (!roundRunning || eventEnding || loser == null) {
            return;
        }

        Player winner = loser.equals(currentP1) ? currentP2 : currentP1;
        if (winner == null) {
            finishRoundDraw();
            return;
        }

        if (isSolo()) {
            int points = soloPoints.getOrDefault(winner, 0) + 1;
            soloPoints.put(winner, points);

            for (String line : config.getStringList("Messages.Round winner")) {
                sendToEvent(line.replace("@winner", winner.getName())
                        .replace("@points", String.valueOf(points)));
            }

            sendRoundWinnerTitle(winner);
            sendStandingsChat();
            endCurrentRoundAndReturnLobby();
            return;
        }

        TeamData winnerTeam = getCurrentWinnerTeam(winner);
        if (winnerTeam == null) {
            finishRoundDraw();
            return;
        }

        if (currentDuoLeg == 1) {
            currentDuoLegWinnerTeam = winnerTeam;

            for (String line : config.getStringList("Messages.Round winner")) {
                sendToEvent(line.replace("@winner", winner.getName())
                        .replace("@points", String.valueOf(winnerTeam.points)));
            }

            sendRoundWinnerTitle(winner);
            sendStandingsChat();
            prepareSecondLeg();
            return;
        }

        if (currentDuoLeg == 2) {
            if (currentDuoLegWinnerTeam == null) {
                currentDuoLegWinnerTeam = winnerTeam;
            }

            if (winnerTeam.equals(currentDuoLegWinnerTeam)) {
                winnerTeam.points++;

                for (String line : config.getStringList("Messages.Round winner")) {
                    sendToEvent(line.replace("@winner", winner.getName())
                            .replace("@points", String.valueOf(winnerTeam.points)));
                }

                sendRoundWinnerTitle(winner);
                sendStandingsChat();
                finishCurrentDuoMatchAndReturnLobby();
                return;
            }

            sendRoundWinnerTitle(winner);
            sendStandingsChat();
            prepareTieBreakLeg();
            return;
        }

        if (currentDuoLeg == 3) {
            winnerTeam.points++;

            for (String line : config.getStringList("Messages.Round winner")) {
                sendToEvent(line.replace("@winner", winner.getName())
                        .replace("@points", String.valueOf(winnerTeam.points)));
            }

            sendRoundWinnerTitle(winner);
            sendStandingsChat();
            finishCurrentDuoMatchAndReturnLobby();
        }
    }

    public void finishRoundDraw() {
        if (!roundRunning || eventEnding) {
            return;
        }

        for (String line : config.getStringList("Messages.Round draw")) {
            sendToEvent(line);
        }

        if (!isSolo() && currentDuoLeg == 1) {
            prepareSecondLeg();
            return;
        }

        if (!isSolo() && currentDuoLeg == 2) {
            prepareTieBreakLeg();
            return;
        }

        sendStandingsChat();
        endCurrentRoundAndReturnLobby();
    }

    public boolean isRoundStarting() {
        return roundStarting;
    }

    public boolean isRoundRunning() {
        return roundRunning;
    }

    public boolean isPvPEnabled() {
        return roundRunning && !roundStarting;
    }

    public double getKnockbackHorizontal() {
        return knockbackHorizontal;
    }

    public double getKnockbackVertical() {
        return knockbackVertical;
    }

    public boolean isJumpPad(Material material) {
        return jumpPadMaterials.contains(material);
    }

    public boolean isSpeedPad(Material material) {
        return speedPadMaterials.contains(material);
    }

    public boolean canUseJumpPad(Player player) {
        return canUseCooldown(player, jumpCooldown, jumpCooldownSeconds);
    }

    public boolean canUseSpeedPad(Player player) {
        return canUseCooldown(player, speedCooldown, speedCooldownSeconds);
    }

    public void markJumpPadUse(Player player) {
        jumpCooldown.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void markSpeedPadUse(Player player) {
        speedCooldown.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public double getJumpPower() {
        return jumpPower;
    }

    public int getSpeedAmplifier() {
        return speedAmplifier;
    }

    public int getSpeedDuration() {
        return speedDuration;
    }

    public Player getCurrentP1() {
        return currentP1;
    }

    public Player getCurrentP2() {
        return currentP2;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }

    private void resetState() {
        currentRound = 0;
        roundRunning = false;
        roundStarting = false;
        eventEnding = false;
        tieBreakMode = false;

        currentArena = null;
        currentP1 = null;
        currentP2 = null;
        currentTeam1 = null;
        currentTeam2 = null;
        currentDuoMatch = null;
        currentDuoLeg = 0;
        currentDuoLegWinnerTeam = null;

        soloPoints.clear();
        teams.clear();
        playerColors.clear();
        jumpCooldown.clear();
        speedCooldown.clear();
        simpleClansPlayers.clear();
        duoInvites.clear();
        availableSoloColors.clear();
        availableTeamColors.clear();
        soloMatches.clear();
        tieBreakSoloMatches.clear();
        duoMatches.clear();
        tieBreakDuoMatches.clear();
    }

    private void prepareAsLobbyPlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        clearPlayer(player);
        teleport(player, "lobby");
    }

    private void prepareAsGlobalSpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        clearPlayer(player);
        teleport(player, "spectator");
    }

    private void prepareModeData() {
        if (isSolo()) {
            return;
        }

        List<Player> players = new ArrayList<>(getPlayers());
        players.removeIf(this::hasTeam);

        if (duoPreferClans && plugin.getSimpleClans() != null) {
            Map<String, List<Player>> byClan = new LinkedHashMap<>();

            for (Player player : players) {
                ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
                String key = clanPlayer != null && clanPlayer.getClan() != null
                        ? clanPlayer.getClan().getTag()
                        : "__NO_CLAN__";
                byClan.computeIfAbsent(key, ignored -> new ArrayList<>()).add(player);
            }

            Set<Player> used = new HashSet<>();
            for (List<Player> sameClan : byClan.values()) {
                while (sameClan.size() >= 2) {
                    Player a = sameClan.remove(0);
                    Player b = sameClan.remove(0);

                    if (used.contains(a) || used.contains(b) || hasTeam(a) || hasTeam(b)) {
                        continue;
                    }

                    createTeam(a, b);
                    used.add(a);
                    used.add(b);
                }
            }

            players.removeIf(used::contains);
        }

        if (duoAutoPair) {
            while (players.size() >= 2) {
                Player a = players.remove(0);
                Player b = players.remove(0);

                if (hasTeam(a) || hasTeam(b)) {
                    continue;
                }

                createTeam(a, b);

                for (String line : config.getStringList("Messages.Duo auto pair")) {
                    a.sendMessage(ColorUtils.colorize(line.replace("@partner", b.getName())));
                    b.sendMessage(ColorUtils.colorize(line.replace("@partner", a.getName())));
                }
            }
        }

        if (!players.isEmpty() && duoEliminateOdd) {
            Player odd = players.get(0);

            for (String line : config.getStringList("Messages.Duo odd eliminated")) {
                sendToEvent(line.replace("@player", odd.getName()));
            }

            removePendingInvites(odd);
            remove(odd);
        }

        for (TeamData team : teams) {
            team.points = 0;
        }
    }

    private void createTeam(Player a, Player b) {
        if (hasTeam(a) || hasTeam(b)) {
            return;
        }

        String uniqueColor = takeNextTeamColor();
        if (uniqueColor == null) {
            return;
        }

        TeamData team = new TeamData();
        team.colorHex = uniqueColor;
        team.members.add(a);
        team.members.add(b);
        teams.add(team);
    }

    private void prepareAvailableColors() {
        List<String> configured = new ArrayList<>(config.getStringList("Team colors"));
        availableSoloColors.addAll(configured);
        availableTeamColors.addAll(configured);
    }

    private boolean hasEnoughColors() {
        int configuredColors = config.getStringList("Team colors").size();
        if (isSolo()) {
            return configuredColors >= getPlayers().size();
        }
        int teamCount = getPlayers().size() / 2;
        return configuredColors >= teamCount;
    }

    private String takeNextSoloColor() {
        if (availableSoloColors.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(availableSoloColors.size());
        return availableSoloColors.remove(index);
    }

    private String takeNextTeamColor() {
        if (availableTeamColors.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(availableTeamColors.size());
        return availableTeamColors.remove(index);
    }

    private void generateSoloMatches() {
        soloMatches.clear();

        List<Player> players = new ArrayList<>(getPlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                soloMatches.add(new SoloMatch(players.get(i), players.get(j)));
            }
        }
    }

    private void generateDuoMatches() {
        duoMatches.clear();

        List<TeamData> localTeams = new ArrayList<>(teams);
        localTeams.sort(Comparator.comparing(team -> team.getDisplayName().toLowerCase(Locale.ROOT)));

        for (int i = 0; i < localTeams.size(); i++) {
            for (int j = i + 1; j < localTeams.size(); j++) {
                duoMatches.add(new DuoMatch(localTeams.get(i), localTeams.get(j)));
            }
        }
    }

    private void processSoloForfeit(Player quitter) {
        List<SoloMatch> affected = new ArrayList<>();

        for (SoloMatch match : soloMatches) {
            if (match.first.equals(quitter) || match.second.equals(quitter)) {
                affected.add(match);
            }
        }

        for (SoloMatch match : affected) {
            Player winner = match.first.equals(quitter) ? match.second : match.first;
            if (getPlayers().contains(winner)) {
                soloPoints.put(winner, soloPoints.getOrDefault(winner, 0) + 1);
            }
            soloMatches.remove(match);
        }

        List<SoloMatch> affectedTie = new ArrayList<>();

        for (SoloMatch match : tieBreakSoloMatches) {
            if (match.first.equals(quitter) || match.second.equals(quitter)) {
                affectedTie.add(match);
            }
        }

        for (SoloMatch match : affectedTie) {
            Player winner = match.first.equals(quitter) ? match.second : match.first;
            if (getPlayers().contains(winner)) {
                soloPoints.put(winner, soloPoints.getOrDefault(winner, 0) + 1);
            }
            tieBreakSoloMatches.remove(match);
        }
    }

    private void processDuoForfeit(Player quitter) {
        TeamData team = getTeam(quitter);
        if (team == null) {
            return;
        }

        for (DuoMatch match : new ArrayList<>(duoMatches)) {
            if (match.hasTeam(team)) {
                TeamData winnerTeam = match.first.equals(team) ? match.second : match.first;
                if (winnerTeam != null) {
                    winnerTeam.points++;
                }
                duoMatches.remove(match);
            }
        }

        for (DuoMatch match : new ArrayList<>(tieBreakDuoMatches)) {
            if (match.hasTeam(team)) {
                TeamData winnerTeam = match.first.equals(team) ? match.second : match.first;
                if (winnerTeam != null) {
                    winnerTeam.points++;
                }
                tieBreakDuoMatches.remove(match);
            }
        }

        for (Player member : new ArrayList<>(team.members)) {
            removePendingInvites(member);
            remove(member);
        }

        teams.remove(team);
    }

    private boolean prepareTieBreakSoloMatches() {
        int max = soloPoints.values().stream().max(Integer::compareTo).orElse(0);

        List<Player> leaders = soloPoints.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .filter(getPlayers()::contains)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        if (leaders.size() <= 1) {
            return false;
        }

        tieBreakSoloMatches.clear();
        tieBreakMode = true;

        for (int i = 0; i < leaders.size(); i++) {
            for (int j = i + 1; j < leaders.size(); j++) {
                tieBreakSoloMatches.add(new SoloMatch(leaders.get(i), leaders.get(j)));
            }
        }

        return !tieBreakSoloMatches.isEmpty();
    }

    private boolean prepareTieBreakDuoMatches() {
        int max = teams.stream().map(team -> team.points).max(Integer::compareTo).orElse(0);

        List<TeamData> leaders = teams.stream()
                .filter(team -> team.points == max)
                .sorted(Comparator.comparing(team -> team.getDisplayName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        if (leaders.size() <= 1) {
            return false;
        }

        tieBreakDuoMatches.clear();
        tieBreakMode = true;

        for (int i = 0; i < leaders.size(); i++) {
            for (int j = i + 1; j < leaders.size(); j++) {
                tieBreakDuoMatches.add(new DuoMatch(leaders.get(i), leaders.get(j)));
            }
        }

        return !tieBreakDuoMatches.isEmpty();
    }

    private void applyRoundVisibility() {
        if (currentP1 == null || currentP2 == null) {
            return;
        }

        List<Player> viewers = new ArrayList<>();

        for (Player player : getPlayers()) {
            if (!player.equals(currentP1) && !player.equals(currentP2)) {
                viewers.add(player);
            }
        }

        viewers.addAll(getSpectators());

        for (Player viewer : viewers) {
            currentP1.hidePlayer(plugin, viewer);
            currentP2.hidePlayer(plugin, viewer);

            viewer.showPlayer(plugin, currentP1);
            viewer.showPlayer(plugin, currentP2);

            for (Player otherViewer : viewers) {
                if (!viewer.equals(otherViewer)) {
                    viewer.hidePlayer(plugin, otherViewer);
                }
            }

            viewer.setSpectatorTarget(null);
        }

        currentP1.showPlayer(plugin, currentP2);
        currentP2.showPlayer(plugin, currentP1);
    }

    private void restoreRoundVisibility() {
        List<Player> everyone = new ArrayList<>();
        everyone.addAll(getPlayers());
        everyone.addAll(getSpectators());

        for (Player a : everyone) {
            for (Player b : everyone) {
                a.showPlayer(plugin, b);
            }
        }
    }

    private void scheduleNextRound(int delaySeconds) {
        cancelTask(nextRoundTask);

        if (!prepareNextRoundMatchup()) {
            return;
        }

        for (Player player : getPlayers()) {
            prepareAsLobbyPlayer(player);
        }

        for (Player spectator : getSpectators()) {
            prepareAsGlobalSpectator(spectator);
        }

        sendNextRoundMessage(delaySeconds);

        if (delaySeconds <= 0) {
            beginPreparedRound();
            return;
        }

        nextRoundTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || eventEnding) {
                return;
            }
            beginPreparedRound();
        }, delaySeconds * 20L);
    }

    private boolean prepareNextRoundMatchup() {
        if (!isHappening() || eventEnding) {
            return false;
        }

        if (isSolo()) {
            List<SoloMatch> source = tieBreakMode ? tieBreakSoloMatches : soloMatches;

            while (!source.isEmpty()) {
                SoloMatch match = source.remove(0);

                if (!getPlayers().contains(match.first) || !getPlayers().contains(match.second)) {
                    continue;
                }

                currentRound++;
                currentP1 = match.first;
                currentP2 = match.second;
                currentTeam1 = null;
                currentTeam2 = null;
                currentArena = randomArena();
                return true;
            }

            if (prepareTieBreakSoloMatches()) {
                return prepareNextRoundMatchup();
            }

            finishEventByScore();
            return false;
        }

        List<DuoMatch> source = tieBreakMode ? tieBreakDuoMatches : duoMatches;

        while (!source.isEmpty()) {
            DuoMatch match = source.remove(0);

            if (!teams.contains(match.first) || !teams.contains(match.second)) {
                continue;
            }

            if (match.first.members.size() < 2 || match.second.members.size() < 2) {
                continue;
            }

            currentRound++;
            currentDuoMatch = match;
            currentDuoLeg = 1;
            currentDuoLegWinnerTeam = null;

            currentTeam1 = match.first;
            currentTeam2 = match.second;
            currentP1 = currentTeam1.members.get(0);
            currentP2 = currentTeam2.members.get(0);
            currentArena = randomArena();
            return true;
        }

        if (prepareTieBreakDuoMatches()) {
            return prepareNextRoundMatchup();
        }

        finishEventByScore();
        return false;
    }

    private ArenaData randomArena() {
        return arenas.get(ThreadLocalRandom.current().nextInt(arenas.size()));
    }

    private void removePendingInvites(Player player) {
        duoInvites.remove(player.getUniqueId());
        duoInvites.entrySet().removeIf(entry -> entry.getValue().equals(player.getUniqueId()));
    }

    private Map<String, String> placeholder(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private void sendConfigMessage(Player player, String path) {
        sendConfigMessage(player, path, new HashMap<>());
    }

    private void sendConfigMessage(Player player, String path, Map<String, String> replacements) {
        List<String> list = config.getStringList(path);
        for (String line : list) {
            String parsed = line;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                parsed = parsed.replace(entry.getKey(), entry.getValue());
            }
            player.sendMessage(ColorUtils.colorize(parsed));
        }
    }
    private void beginPreparedRound() {
        if (!isHappening() || eventEnding || currentArena == null || currentP1 == null || currentP2 == null) {
            return;
        }

        for (String line : config.getStringList("Messages.Round")) {
            sendToEvent(line.replace("@arena", currentArena.display)
                    .replace("@player1", getPlayerColorCode(currentP1) + currentP1.getName())
                    .replace("@player2", getPlayerColorCode(currentP2) + currentP2.getName()));
        }

        sendPlayersToArena();
        startRoundCountdown();
    }

    private void sendPlayersToArena() {
        if (currentArena == null || currentP1 == null || currentP2 == null) {
            return;
        }

        restoreRoundVisibility();

        for (Player player : getPlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            clearPlayer(player);
        }

        for (Player spectator : getSpectators()) {
            clearPlayer(spectator);
            spectator.setGameMode(GameMode.SPECTATOR);
            spectator.setAllowFlight(true);
            spectator.setFlying(true);
        }

        currentP1.setGameMode(GameMode.SURVIVAL);
        currentP1.setAllowFlight(false);
        currentP1.setFlying(false);

        currentP2.setGameMode(GameMode.SURVIVAL);
        currentP2.setAllowFlight(false);
        currentP2.setFlying(false);

        currentP1.teleport(currentArena.spawn1, PlayerTeleportEvent.TeleportCause.PLUGIN);
        currentP2.teleport(currentArena.spawn2, PlayerTeleportEvent.TeleportCause.PLUGIN);

        applyFightItem(currentP1);
        applyFightItem(currentP2);

        equipFighterArmor(currentP1);
        equipFighterArmor(currentP2);

        for (Player player : getPlayers()) {
            if (player.equals(currentP1) || player.equals(currentP2)) {
                continue;
            }

            clearPlayer(player);
            player.setGameMode(GameMode.SPECTATOR);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.teleport(currentArena.spectator, PlayerTeleportEvent.TeleportCause.PLUGIN);

            for (String line : config.getStringList("Messages.Spectator arena")) {
                player.sendMessage(ColorUtils.colorize(
                        line.replace("@arena", currentArena.display)
                ));
            }
        }

        for (Player spectator : getSpectators()) {
            clearPlayer(spectator);
            spectator.setGameMode(GameMode.SPECTATOR);
            spectator.setAllowFlight(true);
            spectator.setFlying(true);
            spectator.teleport(currentArena.spectator, PlayerTeleportEvent.TeleportCause.PLUGIN);

            for (String line : config.getStringList("Messages.Spectator arena")) {
                spectator.sendMessage(ColorUtils.colorize(
                        line.replace("@arena", currentArena.display)
                ));
            }
        }

        applyRoundVisibility();
    }

    private void startRoundCountdown() {
        roundStarting = true;
        roundRunning = false;

        final int[] time = {roundCountdown};

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || eventEnding) {
                cancelTask(countdownTask);
                return;
            }

            if (time[0] == 3) {
                titleToFighters(config.getString("Messages.Countdown.Three", "3"));
            } else if (time[0] == 2) {
                titleToFighters(config.getString("Messages.Countdown.Two", "2"));
            } else if (time[0] == 1) {
                titleToFighters(config.getString("Messages.Countdown.One", "1"));
            } else if (time[0] <= 0) {
                titleToFighters(config.getString("Messages.Countdown.Start", "Valendo!"));
                roundStarting = false;
                roundRunning = true;
                cancelTask(countdownTask);
                startRoundTimer();
                return;
            }

            time[0]--;
        }, 0L, 20L);
    }

    private void startRoundTimer() {
        roundTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || eventEnding || !roundRunning) {
                return;
            }
            finishRoundDraw();
        }, roundMaxTime * 20L);
    }

    private void prepareSecondLeg() {
        if (currentDuoMatch == null) {
            endCurrentRoundAndReturnLobby();
            return;
        }

        roundRunning = false;
        roundStarting = false;
        cancelTask(roundTask);
        cancelTask(countdownTask);

        currentDuoLeg = 2;
        currentP1 = currentDuoMatch.first.members.get(1);
        currentP2 = currentDuoMatch.second.members.get(1);
        currentArena = randomArena();

        schedulePreparedLeg();
    }

    private void prepareTieBreakLeg() {
        if (currentDuoMatch == null) {
            endCurrentRoundAndReturnLobby();
            return;
        }

        roundRunning = false;
        roundStarting = false;
        cancelTask(roundTask);
        cancelTask(countdownTask);

        currentDuoLeg = 3;
        currentP1 = currentDuoMatch.first.members.get(0);
        currentP2 = currentDuoMatch.second.members.get(0);
        currentArena = randomArena();

        schedulePreparedLeg();
    }

    private void schedulePreparedLeg() {
        for (Player player : getPlayers()) {
            prepareAsLobbyPlayer(player);
        }

        for (Player spectator : getSpectators()) {
            prepareAsGlobalSpectator(spectator);
        }

        sendNextRoundMessage(nextRoundDelay);

        nextRoundTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || eventEnding) {
                return;
            }
            beginPreparedRound();
        }, nextRoundDelay * 20L);
    }

    private void finishCurrentDuoMatchAndReturnLobby() {
        currentDuoMatch = null;
        currentDuoLeg = 0;
        currentDuoLegWinnerTeam = null;
        endCurrentRoundAndReturnLobby();
    }

    private void endCurrentRoundAndReturnLobby() {
        cancelTask(roundTask);
        cancelTask(countdownTask);

        roundRunning = false;
        roundStarting = false;

        restoreRoundVisibility();

        for (Player player : new ArrayList<>(getPlayers())) {
            prepareAsLobbyPlayer(player);
        }

        for (Player spectator : new ArrayList<>(getSpectators())) {
            prepareAsGlobalSpectator(spectator);
        }

        currentArena = null;
        currentP1 = null;
        currentP2 = null;
        currentTeam1 = null;
        currentTeam2 = null;

        if (!eventEnding) {
            scheduleNextRound(nextRoundDelay);
        }
    }

    private void finishEventByScore() {
        if (isSolo()) {
            Player winner = soloPoints.entrySet()
                    .stream()
                    .filter(entry -> getPlayers().contains(entry.getKey()))
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (winner == null) {
                stop();
                return;
            }

            tieBreakMode = false;
            winner(winner);
            return;
        }

        TeamData best = teams.stream()
                .max(Comparator.comparingInt(team -> team.points))
                .orElse(null);

        if (best == null) {
            stop();
            return;
        }

        tieBreakMode = false;
        teamWinner(best);
    }

    private void checkEndConditions() {
        if (isSolo()) {
            if (getPlayers().size() <= 1) {
                finishEventByScore();
            }
            return;
        }

        long teamsAlive = teams.stream()
                .filter(team -> team.members.size() >= 2)
                .count();

        if (teamsAlive <= 1) {
            finishEventByScore();
        }
    }

    private void handleCombatLeave(Player leaver) {
        if (!roundRunning && !roundStarting) {
            remove(leaver);
            return;
        }

        Player winner = leaver.equals(currentP1) ? currentP2 : currentP1;

        remove(leaver);

        if (winner == null) {
            finishRoundDraw();
            return;
        }

        if (isSolo()) {
            int points = soloPoints.getOrDefault(winner, 0) + 1;
            soloPoints.put(winner, points);

            for (String line : config.getStringList("Messages.Round winner")) {
                sendToEvent(line.replace("@winner", winner.getName())
                        .replace("@points", String.valueOf(points)));
            }

            sendRoundWinnerTitle(winner);
            sendStandingsChat();
            endCurrentRoundAndReturnLobby();
            return;
        }

        TeamData winnerTeam = getCurrentWinnerTeam(winner);
        if (winnerTeam == null) {
            finishRoundDraw();
            return;
        }

        if (currentDuoLeg == 1) {
            currentDuoLegWinnerTeam = winnerTeam;

            for (String line : config.getStringList("Messages.Round winner")) {
                sendToEvent(line.replace("@winner", winner.getName())
                        .replace("@points", String.valueOf(winnerTeam.points)));
            }

            sendRoundWinnerTitle(winner);
            sendStandingsChat();
            prepareSecondLeg();
            return;
        }

        if (currentDuoLeg == 2) {
            if (currentDuoLegWinnerTeam == null) {
                currentDuoLegWinnerTeam = winnerTeam;
            }

            if (winnerTeam.equals(currentDuoLegWinnerTeam)) {
                winnerTeam.points++;

                for (String line : config.getStringList("Messages.Round winner")) {
                    sendToEvent(line.replace("@winner", winner.getName())
                            .replace("@points", String.valueOf(winnerTeam.points)));
                }

                sendRoundWinnerTitle(winner);
                sendStandingsChat();
                finishCurrentDuoMatchAndReturnLobby();
                return;
            }

            sendRoundWinnerTitle(winner);
            sendStandingsChat();
            prepareTieBreakLeg();
            return;
        }

        if (currentDuoLeg == 3) {
            winnerTeam.points++;

            for (String line : config.getStringList("Messages.Round winner")) {
                sendToEvent(line.replace("@winner", winner.getName())
                        .replace("@points", String.valueOf(winnerTeam.points)));
            }

            sendRoundWinnerTitle(winner);
            sendStandingsChat();
            finishCurrentDuoMatchAndReturnLobby();
        }
    }

    private TeamData getCurrentWinnerTeam(Player winner) {
        if (currentTeam1 != null && currentTeam1.contains(winner)) {
            return currentTeam1;
        }
        if (currentTeam2 != null && currentTeam2.contains(winner)) {
            return currentTeam2;
        }
        return null;
    }

    private boolean canUseCooldown(Player player, Map<UUID, Long> map, int cooldownSeconds) {
        long now = System.currentTimeMillis();
        long last = map.getOrDefault(player.getUniqueId(), 0L);
        return now - last >= cooldownSeconds * 1000L;
    }

    private void loadPads() {
        jumpPadMaterials.clear();
        speedPadMaterials.clear();

        for (String name : config.getStringList("Pads.Jump.Materials")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                jumpPadMaterials.add(material);
            }
        }

        for (String name : config.getStringList("Pads.Speed.Materials")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                speedPadMaterials.add(material);
            }
        }
    }

    private void loadArenas() {
        arenas.clear();

        ConfigurationSection section = config.getConfigurationSection("Arenas");
        if (section == null) {
            throw new IllegalStateException("SuperSmackers: seção Arenas não encontrada.");
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection arena = section.getConfigurationSection(key);
            if (arena == null) {
                continue;
            }

            ArenaData data = new ArenaData();
            data.id = key;
            data.display = arena.getString("Display", key);
            data.spawn1 = loadLocation(arena.getConfigurationSection("Spawn1"));
            data.spawn2 = loadLocation(arena.getConfigurationSection("Spawn2"));
            data.spectator = loadLocation(arena.getConfigurationSection("Spectator"));

            if (data.spawn1 != null && data.spawn2 != null && data.spectator != null) {
                arenas.add(data);
            }
        }

        if (arenas.isEmpty()) {
            throw new IllegalStateException("SuperSmackers: nenhuma arena válida encontrada.");
        }
    }

    private Location loadLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) readAngle(section, "Yaw"),
                (float) readAngle(section, "Pitch")
        );
    }

    private double readAngle(ConfigurationSection section, String key) {
        if (section.contains(key)) {
            return section.getDouble(key);
        }

        String lower = Character.toLowerCase(key.charAt(0)) + key.substring(1);
        return section.getDouble(lower);
    }

    private void applyFightItem(Player player) {
        ConfigurationSection itensSection = config.getConfigurationSection("Itens");
        if (itensSection == null || !config.getBoolean("Itens.Enabled", false)) {
            return;
        }

        EventKitApplier.apply(player, itensSection);
    }

    private void clearPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void equipFighterArmor(Player player) {
        String colorHex;

        if (isSolo()) {
            colorHex = playerColors.getOrDefault(player, "&#00aaff");
        } else {
            TeamData team = getTeam(player);
            colorHex = team != null ? team.colorHex : "&#00aaff";
        }

        Color color = parseArmorColor(colorHex);

        player.getInventory().setHelmet(coloredArmor(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(coloredArmor(Material.LEATHER_CHESTPLATE, color));
        player.getInventory().setLeggings(coloredArmor(Material.LEATHER_LEGGINGS, color));
        player.getInventory().setBoots(coloredArmor(Material.LEATHER_BOOTS, color));
        player.updateInventory();
    }

    private ItemStack coloredArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();

        if (meta != null) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }

        return item;
    }

    private Color parseArmorColor(String hex) {
        String clean = hex.replace("&#", "").replace("#", "");

        if (clean.length() != 6) {
            return Color.BLUE;
        }

        try {
            int r = Integer.parseInt(clean.substring(0, 2), 16);
            int g = Integer.parseInt(clean.substring(2, 4), 16);
            int b = Integer.parseInt(clean.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (Exception ignored) {
            return Color.BLUE;
        }
    }

    private String getPlayerColorCode(Player player) {
        if (!isSolo()) {
            TeamData team = getTeam(player);
            if (team != null) {
                return team.colorHex;
            }
        }

        return playerColors.getOrDefault(player, "&#00aaff");
    }

    private void titleToFighters(String text) {
        String parsed = ColorUtils.colorize(text);

        if (currentP1 != null) {
            currentP1.sendTitle(parsed, "", 0, 20, 0);
        }

        if (currentP2 != null) {
            currentP2.sendTitle(parsed, "", 0, 20, 0);
        }
    }

    private void sendRoundWinnerTitle(Player winner) {
        String titleRaw = config.getString("Messages.Round winner title.Title", "@winner");
        String subtitleRaw = config.getString("Messages.Round winner title.Subtitle", "venceu a rodada!");

        String title = ColorUtils.colorize(
                titleRaw.replace("@winner", getPlayerColorCode(winner) + winner.getName())
        );
        String subtitle = ColorUtils.colorize(
                subtitleRaw.replace("@winner", getPlayerColorCode(winner) + winner.getName())
        );

        for (Player player : getPlayers()) {
            player.sendTitle(title, subtitle, 10, 40, 10);
        }

        for (Player spectator : getSpectators()) {
            spectator.sendTitle(title, subtitle, 10, 40, 10);
        }
    }

    private void sendStandingsChat() {
        for (String line : config.getStringList("Messages.Standings header")) {
            sendToEvent(line);
        }

        if (isSolo()) {
            List<Map.Entry<Player, Integer>> ranking = new ArrayList<>(soloPoints.entrySet());
            ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            for (Map.Entry<Player, Integer> entry : ranking) {
                Player player = entry.getKey();

                for (String line : config.getStringList("Messages.Standings line solo")) {
                    sendToEvent(
                            line.replace("@color", getPlayerColorCode(player))
                                    .replace("@player", player.getName())
                                    .replace("@points", String.valueOf(entry.getValue()))
                    );
                }
            }
        } else {
            List<TeamData> ranking = new ArrayList<>(teams);
            ranking.sort((a, b) -> Integer.compare(b.points, a.points));

            for (TeamData team : ranking) {
                for (String line : config.getStringList("Messages.Standings line duo")) {
                    sendToEvent(
                            line.replace("@color", team.colorHex)
                                    .replace("@team", team.getDisplayName())
                                    .replace("@points", String.valueOf(team.points))
                    );
                }
            }
        }

        for (String line : config.getStringList("Messages.Standings footer")) {
            sendToEvent(line);
        }
    }

    private void startActionbar() {
        cancelTask(actionbarTask);

        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || eventEnding) {
                cancelTask(actionbarTask);
                return;
            }

            if (isSolo()) {
                for (Player player : getPlayers()) {
                    int points = soloPoints.getOrDefault(player, 0);
                    String message = ColorUtils.colorize("&#ffaa00Pontuação: &f" + points);
                    player.sendActionBar(message);
                }
                return;
            }

            for (TeamData team : teams) {
                String message = ColorUtils.colorize(
                        team.colorHex + team.getDisplayName() + " &f- &e" + team.points + " pontos"
                );

                for (Player member : team.members) {
                    if (member != null && member.isOnline()) {
                        member.sendActionBar(message);
                    }
                }
            }
        }, 0L, 20L);
    }

    private void sendNextRoundMessage(int delay) {
        if (currentP1 == null || currentP2 == null) {
            return;
        }

        String first = getPlayerColorCode(currentP1) + currentP1.getName();
        String second = getPlayerColorCode(currentP2) + currentP2.getName();

        for (String line : config.getStringList("Messages.Lobby wait")) {
            sendToEvent(
                    line.replace("@player1", first)
                            .replace("@player2", second)
                            .replace("@time", String.valueOf(delay))
            );
        }
    }

    private void setupFriendlyFire() {
        if (plugin.getSimpleClans() == null) {
            return;
        }

        for (Player player : getPlayers()) {
            ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
            if (clanPlayer != null) {
                simpleClansPlayers.add(clanPlayer);
                clanPlayer.setFriendlyFire(true);
            }
        }
    }

    private TeamData getTeam(Player player) {
        for (TeamData team : teams) {
            if (team.contains(player)) {
                return team;
            }
        }
        return null;
    }

    private void sendToEvent(String text) {
        String parsed = ColorUtils.colorize(text);

        for (Player player : getPlayers()) {
            player.sendMessage(parsed);
        }

        for (Player spectator : getSpectators()) {
            spectator.sendMessage(parsed);
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private boolean isSolo() {
        return mode.equalsIgnoreCase("SOLO");
    }

    private static final class ArenaData {
        private String id;
        private String display;
        private Location spawn1;
        private Location spawn2;
        private Location spectator;
    }

    private static final class SoloMatch {
        private final Player first;
        private final Player second;

        private SoloMatch(Player first, Player second) {
            this.first = first;
            this.second = second;
        }
    }

    private static final class DuoMatch {
        private final TeamData first;
        private final TeamData second;

        private DuoMatch(TeamData first, TeamData second) {
            this.first = first;
            this.second = second;
        }

        private boolean hasTeam(TeamData team) {
            return first.equals(team) || second.equals(team);
        }
    }

    public static final class TeamData {
        private String colorHex;
        private final List<Player> members = new ArrayList<>();
        private int points = 0;

        private boolean contains(Player player) {
            return members.contains(player);
        }

        private String getDisplayName() {
            return members.stream()
                    .map(Player::getName)
                    .collect(Collectors.joining(", "));
        }
    }
}