package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.ThorListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class Thor extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private final YamlConfiguration config;
    private final ThorListener listener = new ThorListener();

    private final int startDelay;
    private final int strikeInterval;
    private final int warningTicks;
    private final int strikesPerWave;
    private final double strikeRadius;
    private final boolean finalPhaseEnabled;
    private final int finalPhaseAtPlayers;
    private final int finalPhaseInterval;
    private final int finalPhaseStrikes;

    private final Random random = new Random();

    private final World arenaWorld;
    private final int arenaMinY;
    private final int arenaMaxY;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    private final List<Location> validStrikeLocations = new ArrayList<>();

    private BukkitTask waveTask;
    private BukkitTask actionbarTask;

    private boolean startedStrikes;
    private int countdown;
    private int runningSeconds;

    private final Set<String> pendingStrikeKeys = new HashSet<>();
    private final List<Stage> stages = new ArrayList<>();

    private static final class Stage {
        private final int time;
        private final int interval;
        private final int strikes;

        private Stage(int time, int interval, int strikes) {
            this.time = time;
            this.interval = interval;
            this.strikes = strikes;
        }
    }

    public Thor(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.startDelay = Math.max(1, config.getInt("Evento.Time", 10));
        this.strikeInterval = Math.max(1, config.getInt("Evento.Delay", 5));
        this.warningTicks = Math.max(1, config.getInt("Evento.Warning ticks", 30));
        this.strikesPerWave = Math.max(1, config.getInt("Evento.Strikes per wave", 1));
        this.strikeRadius = Math.max(0.5D, config.getDouble("Evento.Strike radius", 2.5D));
        this.finalPhaseEnabled = config.getBoolean("Evento.Final phase.Enabled", true);
        this.finalPhaseAtPlayers = Math.max(2, config.getInt("Evento.Final phase.At players", 3));
        this.finalPhaseInterval = Math.max(1, config.getInt("Evento.Final phase.Interval", 2));
        this.finalPhaseStrikes = Math.max(1, config.getInt("Evento.Final phase.Strikes", 3));

        String worldName = config.getString("Locations.Pos1.world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Thor: mundo de Pos1 não encontrado.");
        }

        Location pos1 = new Location(
                world,
                config.getDouble("Locations.Pos1.x"),
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos1.z")
        );

        Location pos2 = new Location(
                world,
                config.getDouble("Locations.Pos2.x"),
                config.getDouble("Locations.Pos2.y"),
                config.getDouble("Locations.Pos2.z")
        );

        this.arenaWorld = world;
        this.arenaMinY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        this.arenaMaxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        this.minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        this.maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        this.minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        this.maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        buildValidStrikeLocations();
        loadStages();
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        this.startedStrikes = false;
        this.countdown = startDelay;
        this.runningSeconds = 0;
        this.pendingStrikeKeys.clear();

        sendStartingMessages();
        startActionbar();

        cancelWaveTask();
        waveTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (!isHappening()) {
                        cancelWaveTask();
                        return;
                    }

                    if (getPlayers().isEmpty()) {
                        noWinner();
                        return;
                    }

                    if (getPlayers().size() == 1) {
                        win(getPlayers().get(0));
                        return;
                    }

                    if (!startedStrikes) {
                        if (countdown <= 0) {
                            startedStrikes = true;
                            runningSeconds = 0;
                            countdown = getCurrentInterval();

                            for (String message : config.getStringList("Messages.Enabled")) {
                                sendToEvent(ColorUtils.colorize(
                                        message.replace("@name", config.getString("Evento.Title"))
                                ));
                            }
                        } else {
                            countdown--;
                        }

                        return;
                    }

                    Stage currentStage = getStageForRunningTime();

                    if (countdown <= 0) {
                        int amount = currentStage != null ? currentStage.strikes : getCurrentStrikesPerWave();

                        List<Location> targets = getUniqueStrikeLocations(amount);
                        for (Location target : targets) {
                            warnAndStrike(target);
                        }

                        countdown = currentStage != null ? currentStage.interval : getCurrentInterval();
                    } else {
                        countdown--;
                    }

                    runningSeconds++;
                },
                0L,
                20L
        );
    }

    private void loadStages() {
        List<Map<?, ?>> mapList = config.getMapList("Evento.Stages");
        if (!mapList.isEmpty()) {
            for (Map<?, ?> map : mapList) {
                int time = toInt(map.get("Time"), 0);
                int interval = Math.max(1, toInt(map.get("Interval"), strikeInterval));
                int strikes = Math.max(1, toInt(map.get("Strikes"), strikesPerWave));
                stages.add(new Stage(time, interval, strikes));
            }

            stages.sort((a, b) -> Integer.compare(a.time, b.time));
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("Evento.Stages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int time = section.getInt(key + ".Time", 0);
                int interval = Math.max(1, section.getInt(key + ".Interval", strikeInterval));
                int strikes = Math.max(1, section.getInt(key + ".Strikes", strikesPerWave));
                stages.add(new Stage(time, interval, strikes));
            }

            stages.sort((a, b) -> Integer.compare(a.time, b.time));
        }
    }

    private void buildValidStrikeLocations() {
        validStrikeLocations.clear();

        if (arenaWorld == null) {
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int y = findHighestInsideArena(x, z);
                if (y == Integer.MIN_VALUE) {
                    continue;
                }

                Location strike = new Location(arenaWorld, x + 0.5, y + 1.0, z + 0.5);
                validStrikeLocations.add(strike);
            }
        }
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }

        return fallback;
    }

    private void warnAndStrike(Location impactLocation) {
        String key = toKey(impactLocation);
        if (pendingStrikeKeys.contains(key)) {
            return;
        }

        pendingStrikeKeys.add(key);
        playWarningEffects(impactLocation);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingStrikeKeys.remove(key);

            if (!isHappening()) {
                return;
            }

            strike(impactLocation);
        }, warningTicks);
    }

    private void playWarningEffects(Location location) {
        for (Player player : getPlayers()) {
            if (player.getWorld().equals(location.getWorld())) {
                player.playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.4F);
            }
        }

        for (Player player : getSpectators()) {
            if (player.getWorld().equals(location.getWorld())) {
                player.playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.4F);
            }
        }

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!isHappening() || !pendingStrikeKeys.contains(toKey(location))) {
                task.cancel();
                return;
            }

            location.getWorld().spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    location.clone().add(0, 0.2, 0),
                    20, 0.4, 0.1, 0.4, 0.02
            );
            location.getWorld().spawnParticle(
                    Particle.CLOUD,
                    location.clone().add(0, 0.1, 0),
                    8, 0.2, 0.05, 0.2, 0.01
            );
        }, 0L, 5L);
    }

    private void strike(Location impactLocation) {
        if (!isHappening()) {
            return;
        }

        impactLocation.getWorld().strikeLightningEffect(impactLocation);
        impactLocation.getWorld().playSound(impactLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0F, 1.0F);

        List<Player> toEliminate = new ArrayList<>();

        for (Player player : new ArrayList<>(getPlayers())) {
            if (!player.isOnline()) {
                continue;
            }

            if (!player.getWorld().equals(impactLocation.getWorld())) {
                continue;
            }

            if (player.getLocation().distanceSquared(impactLocation) <= (strikeRadius * strikeRadius)) {
                toEliminate.add(player);
            }
        }

        for (Player player : toEliminate) {
            eliminate(player);
        }

        if (getPlayers().isEmpty()) {
            noWinner();
            return;
        }

        if (getPlayers().size() == 1) {
            win(getPlayers().get(0));
        }
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player)) {
            return;
        }

        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        for (String message : config.getStringList("Messages.Struck")) {
            sendToEvent(ColorUtils.colorize(
                    message.replace("@player", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        remove(player);
        notifyLeave(player);

        Bukkit.getPluginManager().callEvent(
                new PlayerLoseEvent(
                        player,
                        config.getString("filename", "").replace(".yml", ""),
                        getType()
                )
        );
    }

    public void win(Player player) {
        if (!isHappening()) {
            return;
        }

        List<Player> losers = new ArrayList<>(getPlayers());
        losers.removeIf(target -> target.getUniqueId().equals(player.getUniqueId()));

        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }

        for (String message : config.getStringList("Messages.Winner")) {
            String colorized = ColorUtils.colorize(
                    message.replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            );
            Component component = LegacyComponentSerializer.legacySection().deserialize(colorized);
            Bukkit.getServer().broadcast(component);
        }

        DiscordWebhookManager.sendPlayerWinner(player.getName(), config.getString("Evento.Title"));

        if (plugin.getLeagueManager() != null) {
            plugin.getLeagueManager().handleSoloWin(
                    player,
                    losers,
                    "thor"
            );
        }

        setWinner(player);
        stop();
    }

    public void noWinner() {
        if (!isHappening()) {
            return;
        }

        for (String message : config.getStringList("Messages.No winner")) {
            String colorized = ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            );
            Component component = LegacyComponentSerializer.legacySection().deserialize(colorized);
            Bukkit.getServer().broadcast(component);
        }

        stop();
    }

    private void sendStartingMessages() {
        for (String message : config.getStringList("Messages.Starting")) {
            sendToEvent(ColorUtils.colorize(
                    message.replace("@time", String.valueOf(startDelay))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }
    }

    private void startActionbar() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
        }

        actionbarTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (!isHappening()) {
                        return;
                    }

                    String path = startedStrikes ? "Messages.Actionbar running" : "Messages.Actionbar starting";
                    String message = config.getString(path);

                    if (message == null || message.isEmpty()) {
                        return;
                    }

                    String parsed = ColorUtils.colorize(
                            message.replace("@time", countdown + "s")
                                    .replace("@players", String.valueOf(getPlayers().size()))
                                    .replace("@stage", String.valueOf(getCurrentStageIndex()))
                                    .replace("@name", config.getString("Evento.Title"))
                    );

                    Component component = LegacyComponentSerializer.legacySection().deserialize(parsed);

                    for (Player player : getPlayers()) {
                        player.sendActionBar(component);

                        if (countdown <= 3 && countdown > 0) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.2F);
                        }
                    }

                    for (Player player : getSpectators()) {
                        player.sendActionBar(component);
                    }
                },
                0L,
                20L
        );
    }

    private int getCurrentStageIndex() {
        if (stages.isEmpty()) {
            return 1;
        }

        int index = 1;
        for (int i = 0; i < stages.size(); i++) {
            if (runningSeconds >= stages.get(i).time) {
                index = i + 1;
            } else {
                break;
            }
        }

        return index;
    }

    private int getCurrentInterval() {
        Stage stage = getStageForRunningTime();
        if (stage != null) {
            return stage.interval;
        }

        if (finalPhaseEnabled && getPlayers().size() <= finalPhaseAtPlayers) {
            return finalPhaseInterval;
        }

        return strikeInterval;
    }

    private int getCurrentStrikesPerWave() {
        Stage stage = getStageForRunningTime();
        if (stage != null) {
            return stage.strikes;
        }

        if (finalPhaseEnabled && getPlayers().size() <= finalPhaseAtPlayers) {
            return finalPhaseStrikes;
        }

        return strikesPerWave;
    }

    private Stage getStageForRunningTime() {
        if (stages.isEmpty()) {
            return null;
        }

        Stage current = stages.get(0);
        for (Stage stage : stages) {
            if (runningSeconds >= stage.time) {
                current = stage;
            } else {
                break;
            }
        }
        return current;
    }

    private List<Location> getUniqueStrikeLocations(int amount) {
        List<Location> result = new ArrayList<>();

        if (validStrikeLocations.isEmpty()) {
            return result;
        }

        List<Location> pool = new ArrayList<>(validStrikeLocations);
        Collections.shuffle(pool, random);

        for (Location loc : pool) {
            String key = toKey(loc);

            if (pendingStrikeKeys.contains(key)) {
                continue;
            }

            result.add(loc.clone());

            if (result.size() >= amount) {
                break;
            }
        }

        return result;
    }

    private Location getRandomStrikeLocation() {
        if (validStrikeLocations.isEmpty()) {
            return null;
        }

        List<Location> pool = new ArrayList<>(validStrikeLocations);
        Collections.shuffle(pool, random);

        for (Location loc : pool) {
            if (!pendingStrikeKeys.contains(toKey(loc))) {
                return loc.clone();
            }
        }

        return null;
    }

    private int findHighestInsideArena(int x, int z) {
        if (arenaWorld == null) {
            return Integer.MIN_VALUE;
        }

        for (int y = arenaMaxY; y >= arenaMinY; y--) {
            if (!arenaWorld.getBlockAt(x, y, z).getType().isAir()
                    && arenaWorld.getBlockAt(x, y + 1, z).getType().isAir()) {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    private String toKey(Location location) {
        return location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void sendToEvent(String message) {
        for (Player player : getPlayers()) {
            player.sendMessage(message);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(message);
        }
    }

    @Override
    public void stop() {
        cancelWaveTask();

        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }

        pendingStrikeKeys.clear();

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    private void cancelWaveTask() {
        if (waveTask != null) {
            waveTask.cancel();
            waveTask = null;
        }
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}