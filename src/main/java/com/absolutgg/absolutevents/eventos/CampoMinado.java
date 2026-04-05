package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.CampoMinadoListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.Cuboid;
import com.absolutgg.absolutevents.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CampoMinado extends Evento {

    private final YamlConfiguration config;
    private final CampoMinadoListener listener = new CampoMinadoListener();

    private final Cuboid cuboid;
    private final Block[] borders;
    private final World world;
    private final BlockFace spawnDirection;

    private BukkitTask mainTask;
    private BukkitTask levelStartTask;
    private BukkitTask actionbarTask;

    private int level;
    private int levelTimeLeft;
    private final int delay;
    private final int maxTime;
    private boolean levelHappening;
    private final boolean lastPlayerWin;

    private final Set<UUID> finishedPlayers = new HashSet<>();
    private boolean pressureApplied;

    public CampoMinado(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.delay = this.config.getInt("Evento.Delay");
        this.maxTime = this.config.getInt("Evento.Time");
        this.lastPlayerWin = this.config.getBoolean("Evento.Last player win");
        this.levelHappening = false;
        this.level = 0;
        this.levelTimeLeft = 0;
        this.pressureApplied = false;

        this.world = AbsolutEventsPlugin.getInstance().getServer().getWorld(this.config.getString("Locations.Pos1.world"));

        Location pos1 = new Location(
                world,
                this.config.getDouble("Locations.Pos1.x"),
                this.config.getDouble("Locations.Pos1.y"),
                this.config.getDouble("Locations.Pos1.z")
        );

        Location pos2 = new Location(
                world,
                this.config.getDouble("Locations.Pos2.x"),
                this.config.getDouble("Locations.Pos2.y"),
                this.config.getDouble("Locations.Pos2.z")
        );

        this.cuboid = new Cuboid(pos1, pos2);
        this.borders = cuboid.corners();

        this.spawnDirection = Utils.yawToFace((float) this.config.getDouble("Locations.Entrance.yaw"), false);

        for (Block block : cuboid.getBlocks()) {
            block.setType(Material.GLASS);
        }

        addWall();
    }

    @Override
    public void start() {
        AbsolutEventsPlugin.getInstance().getServer().getPluginManager().registerEvents(listener, AbsolutEventsPlugin.getInstance());
        listener.setEvento();

        mainTask = Bukkit.getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening()) {
                        cancelTask(mainTask);
                        return;
                    }

                    if (!levelHappening) {
                        if (level < config.getInt("Evento.Levels")) {
                            level();
                        } else {
                            win();
                            cancelTask(mainTask);
                        }
                    }
                },
                0L,
                20L
        );
    }

    public void win() {
        List<Player> winnersList = new ArrayList<>(getPlayers());
        List<String> winners = new ArrayList<>();

        this.setWinners();

        for (Player player : winnersList) {
            winners.add(player.getName());
            TournamentStatsManager.getInstance().addWin(player.getUniqueId());
        }

        for (String message : this.config.getStringList("Messages.Winner")) {
            AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", String.join(", ", winners))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        if (!winnersList.isEmpty()) {
            if (winnersList.size() == 1) {
                Player winner = winnersList.get(0);
                List<Player> losers = new ArrayList<>(getPlayers());
                losers.removeIf(target -> target.getUniqueId().equals(winner.getUniqueId()));

                DiscordWebhookManager.sendPlayerWinner(
                        winner.getName(),
                        config.getString("Evento.Title")
                );

                if (AbsolutEventsPlugin.getInstance().getLeagueManager() != null) {
                    AbsolutEventsPlugin.getInstance().getLeagueManager().handleSoloWin(
                            winner,
                            losers,
                            "campominado"
                    );
                }
            } else {
                DiscordWebhookManager.sendMultipleWinners(
                        winnersList.stream().map(Player::getName).toList(),
                        config.getString("Evento.Title")
                );

                if (AbsolutEventsPlugin.getInstance().getLeagueManager() != null) {
                    AbsolutEventsPlugin.getInstance().getLeagueManager().handleMultipleWinners(
                            winnersList,
                            List.of(),
                            "campominado"
                    );
                }
            }
        }

        this.stop();

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    for (Player player : winnersList) {
                        if (!player.isOnline()) {
                            continue;
                        }

                        for (String command : this.config.getStringList("Rewards.Commands")) {
                            executeConsoleCommand(player, command.replace("@winner", player.getName()));
                        }

                        player.updateInventory();
                    }
                },
                20L
        );
    }

    @Override
    public void stop() {
        cancelTask(mainTask);
        cancelTask(levelStartTask);
        cancelTask(actionbarTask);

        this.levelHappening = false;
        this.levelTimeLeft = 0;
        this.pressureApplied = false;
        this.finishedPlayers.clear();

        removeWall();

        for (Block block : cuboid.getBlocks()) {
            block.setType(Material.GLASS);
        }

        HandlerList.unregisterAll(listener);
        this.removePlayers();
    }

    private void level() {
        if (!isHappening()) {
            return;
        }

        level++;
        levelHappening = true;
        levelTimeLeft = maxTime;
        pressureApplied = false;
        finishedPlayers.clear();

        for (Block block : cuboid.getBlocks()) {
            block.setType(Material.GLASS);
        }

        addWall();

        for (Player player : getPlayers()) {
            for (String message : config.getStringList("Messages.Starting level")) {
                player.sendMessage(ColorUtils.colorize(
                        message
                                .replace("@level", String.valueOf(level))
                                .replace("@time", String.valueOf(delay))
                                .replace("@name", this.config.getString("Evento.Title"))
                ));
            }
        }

        for (Player player : getSpectators()) {
            for (String message : config.getStringList("Messages.Starting level")) {
                player.sendMessage(ColorUtils.colorize(
                        message
                                .replace("@level", String.valueOf(level))
                                .replace("@time", String.valueOf(delay))
                                .replace("@name", this.config.getString("Evento.Title"))
                ));
            }
        }

        levelStartTask = Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || !levelHappening) {
                        return;
                    }

                    for (Player player : getPlayers()) {
                        for (String message : config.getStringList("Messages.Next level")) {
                            player.sendMessage(ColorUtils.colorize(
                                    message
                                            .replace("@level", String.valueOf(level))
                                            .replace("@name", config.getString("Evento.Title"))
                            ));
                        }
                    }

                    for (Player player : getSpectators()) {
                        for (String message : config.getStringList("Messages.Next level")) {
                            player.sendMessage(ColorUtils.colorize(
                                    message
                                            .replace("@level", String.valueOf(level))
                                            .replace("@name", config.getString("Evento.Title"))
                            ));
                        }
                    }

                    fill();
                    removeWall();
                    startActionbar();
                },
                delay * 20L
        );
    }

    private void startActionbar() {
        cancelTask(actionbarTask);

        if (!config.getBoolean("Actionbar.Enabled", true)) {
            return;
        }

        actionbarTask = Bukkit.getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || !levelHappening) {
                        cancelTask(actionbarTask);
                        return;
                    }

                    String message = ColorUtils.colorize(
                            config.getString("Actionbar.Message", "&eNível: &f@level &8| &eTempo: &f@time")
                                    .replace("@level", String.valueOf(level))
                                    .replace("@time", String.valueOf(Math.max(levelTimeLeft, 0)))
                    );

                    for (Player player : getPlayers()) {
                        player.sendActionBar(message);
                    }

                    for (Player player : getSpectators()) {
                        player.sendActionBar(message);
                    }

                    if (levelTimeLeft <= 0) {
                        finishLevel();
                        cancelTask(actionbarTask);
                        return;
                    }

                    levelTimeLeft--;
                },
                0L,
                20L
        );
    }

    private void finishLevel() {
        if (!isHappening() || !levelHappening) {
            return;
        }

        List<Player> eliminate = new ArrayList<>();

        for (Player player : new ArrayList<>(getPlayers())) {
            if (cuboid.isInWithMarginY(player, 6)) {
                eliminate.add(player);
                continue;
            }

            if (!hasFinishedLevel(player)) {
                eliminate.add(player);
            }
        }

        for (Player player : eliminate) {
            eliminate(player);
        }

        levelTimeLeft = 0;
        levelHappening = false;
        pressureApplied = false;
        finishedPlayers.clear();
    }

    public void handleLevelProgress(Player player) {
        if (!isHappening() || !levelHappening) {
            return;
        }

        if (player == null || !getPlayers().contains(player)) {
            return;
        }

        if (!hasFinishedLevel(player)) {
            return;
        }

        if (!finishedPlayers.add(player.getUniqueId())) {
            return;
        }

        if (!pressureApplied && config.getBoolean("Pressure.Enabled", true)) {
            applyPressure(player);
        }
    }

    private void applyPressure(Player firstPlayer) {
        pressureApplied = true;

        double multiplier = config.getDouble("Pressure.Remaining multiplier", 0.5D);
        int minimumTime = config.getInt("Pressure.Minimum time", 3);

        int newTime = (int) Math.ceil(levelTimeLeft * multiplier);
        newTime = Math.max(minimumTime, newTime);

        if (newTime < levelTimeLeft) {
            levelTimeLeft = newTime;
        }

        for (String message : config.getStringList("Messages.First finish")) {
            String parsed = ColorUtils.colorize(
                    message
                            .replace("@player", firstPlayer.getName())
                            .replace("@time", String.valueOf(levelTimeLeft))
                            .replace("@level", String.valueOf(level))
                            .replace("@name", config.getString("Evento.Title"))
            );

            for (Player online : getPlayers()) {
                online.sendMessage(parsed);
            }

            for (Player online : getSpectators()) {
                online.sendMessage(parsed);
            }
        }
    }

    private boolean hasFinishedLevel(Player player) {
        double x = player.getLocation().getX();
        double z = player.getLocation().getZ();

        if (level % 2 != 0) {
            switch (spawnDirection) {
                case EAST:
                    return x >= config.getDouble("Locations.Entrance.x") + cuboid.getXWidth();
                case WEST:
                    return x <= config.getDouble("Locations.Entrance.x") - cuboid.getXWidth();
                case SOUTH:
                    return z >= config.getDouble("Locations.Entrance.z") + cuboid.getZWidth();
                case NORTH:
                    return z <= config.getDouble("Locations.Entrance.z") - cuboid.getZWidth();
                default:
                    return false;
            }
        } else {
            switch (spawnDirection) {
                case EAST:
                    return x <= config.getDouble("Locations.Entrance.x");
                case WEST:
                    return x >= config.getDouble("Locations.Entrance.x");
                case SOUTH:
                    return z <= config.getDouble("Locations.Entrance.z");
                case NORTH:
                    return z >= config.getDouble("Locations.Entrance.z");
                default:
                    return false;
            }
        }
    }

    private void fill() {
        int percentage = (int) (cuboid.getTotalBlockSize() * (this.config.getDouble("Evento.Difficulty") * level / 100.0f));
        int i = 0;

        while (i < percentage) {
            Block block = cuboid.getRandomLocation().getBlock();

            if (block.getType() == Material.AIR) {
                continue;
            }

            block.setType(Material.AIR);
            i++;
        }
    }

    private void addWall() {
        for (int x = 0; x < cuboid.getXWidth(); x++) {
            for (int z = 0; z < cuboid.getZWidth(); z++) {
                for (int y = 1; y < 6; y++) {
                    world.getBlockAt(new Location(world, borders[0].getX() + x, borders[0].getY() + y, borders[0].getZ())).setType(Material.GLASS);
                    world.getBlockAt(new Location(world, borders[5].getX() - x, borders[5].getY() + y, borders[5].getZ())).setType(Material.GLASS);
                    world.getBlockAt(new Location(world, borders[2].getX(), borders[2].getY() + y, borders[2].getZ() + z)).setType(Material.GLASS);
                    world.getBlockAt(new Location(world, borders[7].getX(), borders[7].getY() + y, borders[7].getZ() - z)).setType(Material.GLASS);
                }
            }
        }
    }

    private void removeWall() {
        for (int x = 0; x < cuboid.getXWidth(); x++) {
            for (int z = 0; z < cuboid.getZWidth(); z++) {
                for (int y = 1; y < 6; y++) {
                    world.getBlockAt(new Location(world, borders[0].getX() + x, borders[0].getY() + y, borders[0].getZ())).setType(Material.AIR);
                    world.getBlockAt(new Location(world, borders[5].getX() - x, borders[5].getY() + y, borders[5].getZ())).setType(Material.AIR);
                    world.getBlockAt(new Location(world, borders[2].getX(), borders[2].getY() + y, borders[2].getZ() + z)).setType(Material.AIR);
                    world.getBlockAt(new Location(world, borders[7].getX(), borders[7].getY() + y, borders[7].getZ() - z)).setType(Material.AIR);
                }
            }
        }
    }

    public void eliminate(Player player) {
        player.sendMessage(ColorUtils.colorize(
                AbsolutEventsPlugin.getInstance()
                        .getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        for (String message : config.getStringList("Messages.Eliminated player")) {
            String parsed = ColorUtils.colorize(
                    message
                            .replace("@player", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            );

            for (Player online : getPlayers()) {
                online.sendMessage(parsed);
            }

            for (Player online : getSpectators()) {
                online.sendMessage(parsed);
            }
        }

        remove(player);
        notifyLeave(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                getConfig().getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        if (isHappening() && getPlayers().size() == 1 && lastPlayerWin) {
            win();
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}