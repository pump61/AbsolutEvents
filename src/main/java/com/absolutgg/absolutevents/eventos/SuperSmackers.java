package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.SuperSmackersListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.CustomItemResolver;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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


import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

public final class SuperSmackers extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final SuperSmackersListener listener = new SuperSmackersListener();

    private final String mode;
    private final int roundCountdown;
    private final int roundMaxTime;
    private final int totalRounds;

    private final double knockbackHorizontal;
    private final double knockbackVertical;

    private final boolean duoAutoPair;
    private final boolean duoPreferClans;
    private final boolean duoEliminateOdd;

    private final List<ArenaData> arenas = new ArrayList<>();
    private final List<Location> lobbySpectators = new ArrayList<>();
    private final List<TeamData> teams = new ArrayList<>();
    private final Map<Player, Integer> soloPoints = new HashMap<>();
    private final Map<UUID, Long> jumpCooldown = new HashMap<>();
    private final Map<UUID, Long> speedCooldown = new HashMap<>();
    private final List<ClanPlayer> simpleClansPlayers = new ArrayList<>();

    private final List<Material> jumpPadMaterials = new ArrayList<>();
    private final List<Material> speedPadMaterials = new ArrayList<>();

    private final double jumpPower;
    private final int jumpCooldownSeconds;
    private final int speedAmplifier;
    private final int speedDuration;
    private final int speedCooldownSeconds;

    private int currentRound = 0;
    private boolean roundRunning = false;
    private boolean roundStarting = false;
    private boolean eventEnding = false;

    private ArenaData currentArena;
    private Player currentP1;
    private Player currentP2;
    private TeamData currentTeam1;
    private TeamData currentTeam2;

    private BukkitTask roundTask;
    private BukkitTask countdownTask;
    private BukkitTask actionbarTask;

    public SuperSmackers(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.mode = config.getString("Evento.Mode", "SOLO").toUpperCase(Locale.ROOT);
        this.roundCountdown = Math.max(1, config.getInt("Evento.Round countdown", 3));
        this.roundMaxTime = Math.max(5, config.getInt("Evento.Round max time", 60));
        this.totalRounds = Math.max(1, config.getInt("Evento.Total rounds", 5));

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
        loadLobbyLocations();
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        this.roundRunning = false;
        this.roundStarting = false;
        this.eventEnding = false;
        this.currentRound = 0;
        this.currentArena = null;
        this.currentP1 = null;
        this.currentP2 = null;
        this.currentTeam1 = null;
        this.currentTeam2 = null;

        soloPoints.clear();
        teams.clear();
        jumpCooldown.clear();
        speedCooldown.clear();
        simpleClansPlayers.clear();

        for (Player player : getPlayers()) {
            soloPoints.put(player, 0);
            clearPlayer(player);
            applyFightItem(player);
        }

        setupFriendlyFire();
        prepareModeData();

        for (String line : config.getStringList("Messages.Start")) {
            sendToEvent(
                    line.replace("@name", config.getString("Evento.Title"))
            );
        }

        startActionbar();
        startNextRound();
    }

    @Override
    public void stop() {
        cancelTask(roundTask);
        cancelTask(countdownTask);
        cancelTask(actionbarTask);

        for (ClanPlayer clanPlayer : simpleClansPlayers) {
            clanPlayer.setFriendlyFire(false);
        }

        simpleClansPlayers.clear();
        soloPoints.clear();
        teams.clear();
        jumpCooldown.clear();
        speedCooldown.clear();

        currentArena = null;
        currentP1 = null;
        currentP2 = null;
        currentTeam1 = null;
        currentTeam2 = null;
        roundRunning = false;
        roundStarting = false;
        eventEnding = false;

        for (Player player : new ArrayList<>(getPlayers())) {
            clearPlayer(player);
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

        clearPlayer(player);
        soloPoints.remove(player);
        removeFromTeams(player);

        if (player.equals(currentP1) || player.equals(currentP2)) {
            handleCombatLeave(player);
        } else {
            remove(player);
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

        for (String line : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    line.replace("@name", config.getString("Evento.Title"))
                            .replace("@winner", player.getName())
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(
                player.getName(),
                config.getString("Evento.Title")
        );

        setWinner(player);

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
                    line.replace("@name", config.getString("Evento.Title"))
                            .replace("@team", team.getDisplayName())
            ));
        }

        if (!team.members.isEmpty()) {
            setWinners(new HashSet<>(team.members));
        }

        DiscordWebhookManager.sendTeamWinner(
                team.getDisplayName(),
                config.getString("Evento.Title"),
                List.of()
        );

        for (Player player : team.members) {
            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(player, command.replace("@winner", player.getName()));
            }
        }

        stop();
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
        } else {
            TeamData winnerTeam = currentTeam1 != null && currentTeam1.contains(winner) ? currentTeam1 : currentTeam2;
            if (winnerTeam != null) {
                winnerTeam.points++;

                for (String line : config.getStringList("Messages.Round winner")) {
                    sendToEvent(line.replace("@winner", winner.getName())
                            .replace("@points", String.valueOf(winnerTeam.points)));
                }
            }
        }

        endCurrentRoundAndReturnLobby();
        checkEndConditions();
    }

    public void finishRoundDraw() {
        if (!roundRunning || eventEnding) {
            return;
        }

        for (String line : config.getStringList("Messages.Round draw")) {
            sendToEvent(line);
        }

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

    private void prepareModeData() {
        if (isSolo()) {
            return;
        }

        List<Player> players = new ArrayList<>(getPlayers());

        if (duoPreferClans && plugin.getSimpleClans() != null) {
            Map<String, List<Player>> byClan = new LinkedHashMap<>();

            for (Player player : players) {
                ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
                String key = clanPlayer != null && clanPlayer.getClan() != null ? clanPlayer.getClan().getTag() : "__NO_CLAN__";
                byClan.computeIfAbsent(key, k -> new ArrayList<>()).add(player);
            }

            Set<Player> used = new HashSet<>();
            for (List<Player> sameClan : byClan.values()) {
                while (sameClan.size() >= 2) {
                    Player a = sameClan.remove(0);
                    Player b = sameClan.remove(0);
                    if (used.contains(a) || used.contains(b)) {
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

            remove(odd);
        }
    }

    private void createTeam(Player a, Player b) {
        TeamData team = new TeamData();
        team.colorName = getRandomConfiguredColorName();
        team.members.add(a);
        team.members.add(b);
        teams.add(team);
    }

    private void startNextRound() {
        if (!isHappening() || eventEnding) {
            return;
        }

        currentRound++;
        if (currentRound > totalRounds) {
            finishEventByScore();
            return;
        }

        if (isSolo()) {
            List<Player> alive = new ArrayList<>(getPlayers());
            if (alive.size() < 2) {
                finishEventByScore();
                return;
            }

            Collections.shuffle(alive);
            currentP1 = alive.get(0);
            currentP2 = alive.get(1);
            currentTeam1 = null;
            currentTeam2 = null;
        } else {
            List<TeamData> available = teams.stream()
                    .filter(team -> team.members.size() >= 2)
                    .collect(Collectors.toList());

            if (available.size() < 2) {
                finishEventByScore();
                return;
            }

            Collections.shuffle(available);
            currentTeam1 = available.get(0);
            currentTeam2 = available.get(1);

            currentP1 = currentTeam1.getRandomMember();
            currentP2 = currentTeam2.getRandomMember();

            if (currentP1 == null || currentP2 == null) {
                finishEventByScore();
                return;
            }
        }

        currentArena = arenas.get(ThreadLocalRandom.current().nextInt(arenas.size()));

        for (String line : config.getStringList("Messages.Round")) {
            sendToEvent(line.replace("@arena", currentArena.display)
                    .replace("@player1", currentP1.getName())
                    .replace("@player2", currentP2.getName()));
        }

        sendPlayersToArena();
        startRoundCountdown();
    }

    private void sendPlayersToArena() {
        if (currentArena == null) {
            return;
        }

        currentP1.teleport(currentArena.spawn1, PlayerTeleportEvent.TeleportCause.PLUGIN);
        currentP2.teleport(currentArena.spawn2, PlayerTeleportEvent.TeleportCause.PLUGIN);

        clearPlayer(currentP1);
        clearPlayer(currentP2);
        applyFightItem(currentP1);
        applyFightItem(currentP2);

        equipRandomColorArmor(currentP1);
        equipRandomColorArmor(currentP2);

        for (Player player : getPlayers()) {
            if (player.equals(currentP1) || player.equals(currentP2)) {
                continue;
            }

            player.teleport(currentArena.spectator, PlayerTeleportEvent.TeleportCause.PLUGIN);

            for (String line : config.getStringList("Messages.Spectator arena")) {
                player.sendMessage(ColorUtils.colorize(
                        line.replace("@arena", currentArena.display)
                ));
            }
        }
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

    private void endCurrentRoundAndReturnLobby() {
        cancelTask(roundTask);
        cancelTask(countdownTask);

        roundRunning = false;
        roundStarting = false;

        for (Player player : new ArrayList<>(getPlayers())) {
            clearPlayer(player);
            teleport(player, "lobby");
        }

        currentArena = null;
        currentP1 = null;
        currentP2 = null;
        currentTeam1 = null;
        currentTeam2 = null;

        Bukkit.getScheduler().runTaskLater(plugin, this::startNextRound, 60L);
    }

    private void finishEventByScore() {
        if (isSolo()) {
            Player winner = soloPoints.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (winner == null) {
                stop();
                return;
            }

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

        teamWinner(best);
    }

    private void checkEndConditions() {
        if (isSolo()) {
            if (getPlayers().size() <= 1) {
                finishEventByScore();
            }
            return;
        }

        long teamsAlive = teams.stream().filter(team -> team.members.size() >= 2).count();
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
        } else {
            TeamData team = currentTeam1 != null && currentTeam1.contains(winner) ? currentTeam1 : currentTeam2;
            if (team != null) {
                team.points++;
            }
        }

        endCurrentRoundAndReturnLobby();
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

    private void loadLobbyLocations() {
        Location spectator = loadLocation(config.getConfigurationSection("Locations.Spectator"));
        if (spectator != null) {
            lobbySpectators.add(spectator);
        }
    }

    private Location loadLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        if (worldName == null) {
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
        ConfigurationSection section = config.getConfigurationSection("Itens.Inventory.0");
        if (section == null) {
            return;
        }

        ItemStack stack = CustomItemResolver.resolve(section);
        if (stack != null) {
            player.getInventory().setItem(0, stack);
            player.updateInventory();
        }
    }

    private void clearPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void equipRandomColorArmor(Player player) {
        Color color = randomArmorColor();

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

    private Color randomArmorColor() {
        List<String> colors = config.getStringList("Team colors");
        if (colors.isEmpty()) {
            return Color.BLUE;
        }

        String name = colors.get(ThreadLocalRandom.current().nextInt(colors.size())).toUpperCase(Locale.ROOT);
        return switch (name) {
            case "RED" -> Color.RED;
            case "GREEN" -> Color.GREEN;
            case "YELLOW" -> Color.YELLOW;
            case "PURPLE" -> Color.PURPLE;
            case "AQUA" -> Color.AQUA;
            case "GOLD" -> Color.ORANGE;
            case "PINK" -> Color.FUCHSIA;
            default -> Color.BLUE;
        };
    }

    private String getRandomConfiguredColorName() {
        List<String> colors = config.getStringList("Team colors");
        if (colors.isEmpty()) {
            return "BLUE";
        }
        return colors.get(ThreadLocalRandom.current().nextInt(colors.size()));
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

    private void removeFromTeams(Player player) {
        for (TeamData team : teams) {
            team.members.remove(player);
        }
    }

    private void startActionbar() {
        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || eventEnding) {
                return;
            }

            for (Player player : getPlayers()) {
                if (isSolo()) {
                    String line = config.getStringList("Messages.Actionbar solo").stream().findFirst()
                            .orElse("&fRodada: @round/@total_rounds &fPontos: @points");

                    player.sendActionBar(ColorUtils.colorize(
                            line.replace("@round", String.valueOf(currentRound))
                                    .replace("@total_rounds", String.valueOf(totalRounds))
                                    .replace("@points", String.valueOf(soloPoints.getOrDefault(player, 0)))
                    ));
                } else {
                    TeamData team = getTeam(player);
                    int points = team == null ? 0 : team.points;

                    String line = config.getStringList("Messages.Actionbar duo").stream().findFirst()
                            .orElse("&fRodada: @round/@total_rounds &fPontos do time: @points");

                    player.sendActionBar(ColorUtils.colorize(
                            line.replace("@round", String.valueOf(currentRound))
                                    .replace("@total_rounds", String.valueOf(totalRounds))
                                    .replace("@points", String.valueOf(points))
                    ));
                }
            }
        }, 0L, 20L);
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

    private static final class TeamData {
        private String colorName;
        private final List<Player> members = new ArrayList<>();
        private int points = 0;

        private boolean contains(Player player) {
            return members.contains(player);
        }

        private Player getRandomMember() {
            if (members.isEmpty()) {
                return null;
            }
            return members.get(ThreadLocalRandom.current().nextInt(members.size()));
        }

        private String getDisplayName() {
            return colorName + " (" + members.stream().map(Player::getName).collect(Collectors.joining(", ")) + ")";
        }
    }
}