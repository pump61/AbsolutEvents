package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.AnvilListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Anvil extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private final YamlConfiguration config;
    private final AnvilListener listener = new AnvilListener();

    private final int height;
    private final int time;
    private final int delay;

    private final Random random = new Random();

    private BukkitTask startTask;
    private BukkitTask rainTask;
    private BukkitTask actionbarTask;

    private final List<Block> anvils = new ArrayList<>();
    private final List<Stage> stages = new ArrayList<>();

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

        private Stage(int time, int interval) {
            this.time = time;
            this.interval = interval;
        }
    }

    public Anvil(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.height = Math.max(1, config.getInt("Evento.Height"));
        this.time = Math.max(1, config.getInt("Evento.Time"));
        this.delay = Math.max(1, config.getInt("Evento.Delay"));
        this.started = false;
        this.ending = false;
        this.countdown = time;
        this.runningSeconds = 0;

        loadArena();
        loadStages();
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
                                    Block target = getRandomFreeBlock();
                                    if (target == null) {
                                        if (getPlayers().isEmpty()) {
                                            noWinner();
                                        } else if (getPlayers().size() == 1) {
                                            winner(getPlayers().get(0));
                                        } else {
                                            noWinner();
                                        }
                                        return;
                                    }

                                    spawnAnvil(target);
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

    private void loadStages() {
        List<Map<?, ?>> mapList = config.getMapList("Evento.Stages");
        if (!mapList.isEmpty()) {
            for (Map<?, ?> map : mapList) {
                int timeValue = toInt(map.get("Time"), 0);
                int intervalValue = Math.max(1, toInt(map.get("Interval"), delay));
                stages.add(new Stage(timeValue, intervalValue));
            }
            stages.sort((a, b) -> Integer.compare(a.time, b.time));
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("Evento.Stages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int timeValue = section.getInt(key + ".Time", 0);
                int intervalValue = Math.max(1, section.getInt(key + ".Interval", delay));
                stages.add(new Stage(timeValue, intervalValue));
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
        if (stages.isEmpty()) {
            return delay;
        }

        Stage current = stages.get(0);
        for (Stage stage : stages) {
            if (runningSeconds >= stage.time) {
                current = stage;
            } else {
                break;
            }
        }

        return current.interval;
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
        fallingBlock.setHurtEntities(false);

        anvils.add(targetBlock);
    }

    private Block getRandomFreeBlock() {
        if (world == null) {
            return null;
        }

        List<Block> freeBlocks = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, floorY, z);
                if (!anvils.contains(block)) {
                    freeBlocks.add(block);
                }
            }
        }

        if (freeBlocks.isEmpty()) {
            return null;
        }

        return freeBlocks.get(random.nextInt(freeBlocks.size()));
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
                                    .replace("@name", config.getString("Evento.Title"))
                    );

                    for (Player player : getPlayers()) {
                        player.sendActionBar(parsed);
                    }

                    for (Player player : getSpectators()) {
                        player.sendActionBar(parsed);
                    }
                },
                0L,
                20L
        );
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player) || ending) {
            return;
        }

        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
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