package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.AnvilListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class Anvil extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private final YamlConfiguration config;
    private final AnvilListener listener = new AnvilListener();

    private final int height;
    private final int time;
    private final int delay;
    private final int strikesPerWave;

    private final Random random = new Random();

    private BukkitTask startTask;
    private BukkitTask rainTask;
    private BukkitTask actionbarTask;

    private final List<Block> anvils = new ArrayList<>();
    private final List<Stage> stages = new ArrayList<>();
    private final List<Block> arenaBlocks = new ArrayList<>();

    private int minX;
    private int maxX;
    private int minZ;
    private int maxZ;
    private int floorY;
    private World world;

    private boolean started;
    private boolean ending;
    private int countdown;
    private int runningSeconds;

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

    public Anvil(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.height = Math.max(1, config.getInt("Evento.Height"));
        this.time = Math.max(1, config.getInt("Evento.Time"));
        this.delay = Math.max(1, config.getInt("Evento.Delay"));
        this.strikesPerWave = Math.max(1, config.getInt("Evento.Strikes per wave", 1));
        this.started = false;
        this.ending = false;
        this.countdown = time;
        this.runningSeconds = 0;

        loadArena();
        loadStages();
        buildArenaBlocks();
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        this.started = false;
        this.ending = false;
        this.countdown = time;
        this.runningSeconds = 0;
        this.anvils.clear();

        for (Player player : new ArrayList<>(getPlayers())) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.JUMP_BOOST,
                    Integer.MAX_VALUE,
                    250,
                    false,
                    false,
                    false
            ));
        }

        for (String message : config.getStringList("Messages.Starting")) {
            sendToEvent(ColorUtils.colorize(
                    message
                            .replace("@time", String.valueOf(time))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        startActionbar();

        startTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!isHappening() || ending) {
                        return;
                    }

                    started = true;
                    runningSeconds = 0;
                    countdown = getCurrentInterval();

                    rainTask = Bukkit.getScheduler().runTaskTimer(
                            plugin,
                            () -> {
                                if (!isHappening() || ending) {
                                    cancelRainTask();
                                    return;
                                }

                                if (getPlayers().isEmpty()) {
                                    noWinner();
                                    return;
                                }

                                if (getPlayers().size() == 1) {
                                    winner(getPlayers().get(0));
                                    return;
                                }

                                if (countdown <= 0) {
                                    int strikes = getCurrentStrikesPerWave();
                                    List<Block> targets = getUniqueFreeBlocks(strikes);

                                    if (targets.isEmpty()) {
                                        if (getPlayers().isEmpty()) {
                                            noWinner();
                                        } else if (getPlayers().size() == 1) {
                                            winner(getPlayers().get(0));
                                        } else {
                                            winners(new ArrayList<>(getPlayers()));
                                        }
                                        return;
                                    }

                                    for (Block target : targets) {
                                        spawnAnvil(target);
                                    }

                                    countdown = getCurrentInterval();
                                } else {
                                    countdown--;
                                }

                                runningSeconds++;
                            },
                            0L,
                            20L
                    );
                },
                time * 20L
        );
    }

    private void loadArena() {
        String worldName = config.getString("Locations.Pos1.world");
        this.world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        int x1 = (int) Math.floor(config.getDouble("Locations.Pos1.x"));
        int y1 = (int) Math.floor(config.getDouble("Locations.Pos1.y"));
        int z1 = (int) Math.floor(config.getDouble("Locations.Pos1.z"));

        int x2 = (int) Math.floor(config.getDouble("Locations.Pos2.x"));
        int y2 = (int) Math.floor(config.getDouble("Locations.Pos2.y"));
        int z2 = (int) Math.floor(config.getDouble("Locations.Pos2.z"));

        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxZ = Math.max(z1, z2);
        this.floorY = Math.max(y1, y2);
    }

    private void buildArenaBlocks() {
        arenaBlocks.clear();

        if (world == null) {
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                arenaBlocks.add(world.getBlockAt(x, floorY, z));
            }
        }
    }

    private void loadStages() {
        List<Map<?, ?>> mapList = config.getMapList("Evento.Stages");
        if (!mapList.isEmpty()) {
            for (Map<?, ?> map : mapList) {
                int timeValue = toInt(map.get("Time"), 0);
                int intervalValue = Math.max(1, toInt(map.get("Interval"), delay));
                int strikesValue = Math.max(1, toInt(map.get("Strikes"), strikesPerWave));
                stages.add(new Stage(timeValue, intervalValue, strikesValue));
            }
            stages.sort((a, b) -> Integer.compare(a.time, b.time));
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("Evento.Stages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int timeValue = section.getInt(key + ".Time", 0);
                int intervalValue = Math.max(1, section.getInt(key + ".Interval", delay));
                int strikesValue = Math.max(1, section.getInt(key + ".Strikes", strikesPerWave));
                stages.add(new Stage(timeValue, intervalValue, strikesValue));
            }
            stages.sort((a, b) -> Integer.compare(a.time, b.time));
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

    private int getCurrentInterval() {
        Stage stage = getCurrentStage();
        return stage != null ? stage.interval : delay;
    }

    private int getCurrentStrikesPerWave() {
        Stage stage = getCurrentStage();
        return stage != null ? stage.strikes : strikesPerWave;
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

    private Stage getCurrentStage() {
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

    private void spawnAnvil(Block targetBlock) {
        if (world == null || targetBlock == null) {
            return;
        }

        Location spawn = new Location(
                world,
                targetBlock.getX() + 0.5,
                floorY + height,
                targetBlock.getZ() + 0.5
        );

        FallingBlock fallingBlock = world.spawnFallingBlock(
                spawn,
                Bukkit.createBlockData(Material.ANVIL)
        );

        fallingBlock.setDropItem(false);
        fallingBlock.setHurtEntities(true);
        fallingBlock.setGravity(true);
        fallingBlock.setVelocity(new Vector(0.0, -0.35, 0.0));
        fallingBlock.getPersistentDataContainer().set(
                listener.getAnvilKey(),
                PersistentDataType.BYTE,
                (byte) 1
        );

        anvils.add(targetBlock);
    }

    private List<Block> getUniqueFreeBlocks(int amount) {
        if (world == null || arenaBlocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Block> freeBlocks = new ArrayList<>();
        Set<Block> occupied = new HashSet<>(anvils);

        for (Block block : arenaBlocks) {
            if (!occupied.contains(block)) {
                freeBlocks.add(block);
            }
        }

        if (freeBlocks.isEmpty()) {
            return Collections.emptyList();
        }

        Collections.shuffle(freeBlocks, random);

        List<Block> result = new ArrayList<>();
        for (Block block : freeBlocks) {
            result.add(block);
            if (result.size() >= amount) {
                break;
            }
        }

        return result;
    }

    private void startActionbar() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
        }

        actionbarTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (!isHappening() || ending) {
                        return;
                    }

                    String path = started ? "Messages.Actionbar running" : "Messages.Actionbar starting";
                    String message = config.getString(path);

                    if (message == null || message.isEmpty()) {
                        return;
                    }

                    String parsed = ColorUtils.colorize(
                            message
                                    .replace("@time", countdown + "s")
                                    .replace("@players", String.valueOf(getPlayers().size()))
                                    .replace("@stage", String.valueOf(getCurrentStageIndex()))
                                    .replace("@strikes", String.valueOf(getCurrentStrikesPerWave()))
                                    .replace("@interval", String.valueOf(getCurrentInterval()))
                                    .replace("@name", config.getString("Evento.Title"))
                    );

                    Component component = LegacyComponentSerializer.legacySection().deserialize(parsed);

                    for (Player player : getPlayers()) {
                        player.sendActionBar(component);

                        if (!started && countdown <= 3 && countdown > 0) {
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.2F);
                        }
                    }

                    for (Player player : getSpectators()) {
                        player.sendActionBar(component);
                    }
                },
                0L,
                10L
        );
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player) || ending) {
            return;
        }

        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig().getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        remove(player);

        if (getPlayers().isEmpty()) {
            noWinner();
            return;
        }

        if (getPlayers().size() == 1) {
            winner(getPlayers().get(0));
        }
    }

    public List<Block> getAnvils() {
        return anvils;
    }

    public void markAnvilResolved(Block block) {
        anvils.remove(block);
    }

    public void winner(Player player) {
        if (ending) {
            return;
        }

        ending = true;
        setWinner(player);

        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(
                player.getName(),
                config.getString("Evento.Title")
        );

        stop();
    }

    

    public void noWinner() {
        if (ending) {
            return;
        }

        ending = true;

        for (String message : config.getStringList("Messages.No winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }

        stop();
    }

    public void winners(List<Player> players) {
        if (ending || players == null || players.isEmpty()) {
            return;
        }

        ending = true;

        List<String> names = new ArrayList<>();
        for (Player player : players) {
            names.add(player.getName());
            TournamentStatsManager.getInstance().addWin(player.getUniqueId());
        }

        String joinedNames = String.join(", ", names);
        String winnersCount = String.valueOf(players.size());

        List<String> messages = config.getStringList("Messages.Winners");
        if (messages == null || messages.isEmpty()) {
            messages = config.getStringList("Messages.Winner");
        }

        for (String message : messages) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", joinedNames)
                            .replace("@winners", joinedNames)
                            .replace("@winners_count", winnersCount)
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendTeamWinner(
                joinedNames,
                config.getString("Evento.Title"),
                List.of()
        );

        for (Player player : players) {
            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(player, command.replace("@winner", player.getName()));
            }
        }

        stop();
    }

    @Override
    public void stop() {
        if (startTask != null) {
            startTask.cancel();
            startTask = null;
        }

        cancelRainTask();

        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }

        for (Player player : new ArrayList<>(getPlayers())) {
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }

        this.anvils.clear();

        HandlerList.unregisterAll(listener);
        this.removePlayers();
    }

    private void cancelRainTask() {
        if (rainTask != null) {
            rainTask.cancel();
            rainTask = null;
        }
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
    public YamlConfiguration getConfig() {
        return config;
    }
}