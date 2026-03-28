package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.TNTRunListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.Cuboid;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TNTRun extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private final YamlConfiguration config;
    private final TNTRunListener listener = new TNTRunListener();
    private final BukkitScheduler scheduler = plugin.getServer().getScheduler();

    private final Cuboid cuboid;
    private final HashMap<XMaterial, List<Block>> pattern = new HashMap<>();
    private final List<Material> triggers;

    private final int startTime;
    private final long delay;

    private boolean tntRunHappening;
    private boolean ending;
    private BukkitTask startTask;

    public TNTRun(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.tntRunHappening = false;
        this.ending = false;

        this.startTime = config.getInt("Evento.Start");
        this.delay = config.getInt("Evento.Delay");

        this.triggers = config.getStringList("Evento.Trigger blocks").stream()
                .map(XMaterial::matchXMaterial)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(XMaterial::parseMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        World world = plugin.getServer().getWorld(config.getString("Locations.Pos1.world"));

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

        this.cuboid = new Cuboid(pos1, pos2);

        for (Block block : cuboid.getBlocks()) {
            XMaterial xMaterial = XMaterial.matchXMaterial(block.getType());
            pattern.computeIfAbsent(xMaterial, ignored -> new ArrayList<>()).add(block);
        }
    }

    @Override
    public void start() {
        ending = false;
        tntRunHappening = false;

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        for (String message : config.getStringList("Messages.Start evento")) {
            sendToEvent(
                    ColorUtils.colorize(
                            message
                                    .replace("@name", config.getString("Evento.Title"))
                                    .replace("@time", String.valueOf(startTime))
                    )
            );
        }

        startTask = scheduler.runTaskLater(plugin, () -> {
            if (!isHappening() || ending) {
                return;
            }

            this.tntRunHappening = true;
        }, startTime * 20L);
    }

    @Override
    public void stop() {
        tntRunHappening = false;
        ending = false;

        if (startTask != null) {
            startTask.cancel();
            startTask = null;
        }

        for (var entry : pattern.entrySet()) {
            Material material = entry.getKey().parseMaterial();
            if (material == null) {
                continue;
            }

            for (Block block : entry.getValue()) {
                block.setType(material, false);
            }
        }

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    @Override
    public void winner(Player player) {
        if (ending) {
            return;
        }

        ending = true;

        for (String message : config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(
                    ColorUtils.colorize(
                            message
                                    .replace("@winner", player.getName())
                                    .replace("@name", config.getString("Evento.Title"))
                    )
            );
        }

        DiscordWebhookManager.sendPlayerWinner(player.getName(), config.getString("Evento.Title"));

        setWinner(player);

        String winnerName = player.getName();

        stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayerExact(winnerName);
            if (online == null || !online.isOnline()) {
                return;
            }

            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(online, command.replace("@winner", online.getName()));
            }
        }, 5L);
    }

    private void sendToEvent(String message) {
        for (Player player : getPlayers()) {
            player.sendMessage(message);
        }

        for (Player spectator : getSpectators()) {
            spectator.sendMessage(message);
        }
    }

    public BukkitScheduler getScheduler() {
        return scheduler;
    }

    public Cuboid getCuboid() {
        return cuboid;
    }

    public boolean isTntRunHappening() {
        return tntRunHappening;
    }

    public List<Material> getTriggers() {
        return triggers;
    }

    public long getDelay() {
        return delay;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}